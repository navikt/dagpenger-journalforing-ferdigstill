package no.nav.dagpenger.journalføring.ferdigstill

import com.github.kittinunf.result.Result
import io.prometheus.client.Histogram
import mu.KotlinLogging
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.Metrics.inngangsvilkårResultatTellerInc
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.FAGSAK_ID
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.OPPGAVE_ID
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.JournalpostApi
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ManuellJournalføringsOppgaveClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.OppdaterJournalpostPayload
import no.nav.dagpenger.journalføring.ferdigstill.adapter.StartVedtakCommand
import no.nav.dagpenger.journalføring.ferdigstill.adapter.VurderFornyetRettighetCommand
import no.nav.dagpenger.journalføring.ferdigstill.adapter.VurderHenvendelseAngåendeEksisterendeSaksforholdCommand
import no.nav.dagpenger.journalføring.ferdigstill.adapter.createArenaTilleggsinformasjon
import no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester.Vilkårtester

private val logger = KotlinLogging.logger {}

const val ENHET_FOR_HURTIG_AVSLAG_IKKE_PERMITTERT = "4451"
const val ENHET_FOR_HURTIG_AVSLAG_PERMITTERT = "4456"

private val chainTimeSpent = Histogram.build()
    .name("time_spent_in_chain")
    .help("Time spent on each chain")
    .labelNames("chain_name")
    .register()

// GoF pattern - Chain of responsibility (https://en.wikipedia.org/wiki/Chain-of-responsibility_pattern)
abstract class BehandlingsChain(protected val neste: BehandlingsChain? = null) {
    abstract fun håndter(packet: Packet): Packet
    abstract fun kanBehandle(packet: Packet): Boolean
}

fun BehandlingsChain.instrument(handler: () -> Packet): Packet {
    val timer = chainTimeSpent
        .labels(this.javaClass.simpleName.toString())
        .startTimer()

    return handler().also { timer.observeDuration() }
}

internal class FornyetRettighetBehandlingsChain(
    private val arena: ArenaClient,
    neste: BehandlingsChain?
) : BehandlingsChain(neste) {

    override fun håndter(packet: Packet) = instrument {
        if (kanBehandle(packet)) {
            logger.info { "Fornyet rettighet" }
            val tilleggsinformasjon =
                createArenaTilleggsinformasjon(
                    PacketMapper.dokumentTitlerFrom(packet),
                    PacketMapper.registrertDatoFrom(packet)
                )

            val result = arena.bestillOppgave(
                VurderFornyetRettighetCommand(
                    naturligIdent = PacketMapper.bruker(packet).id,
                    behandlendeEnhetId = "4455COR", // todo: Sjekke opp navnet
                    tilleggsinformasjon = tilleggsinformasjon,
                    registrertDato = PacketMapper.registrertDatoFrom(packet),
                    oppgavebeskrivelse = "TODO?"
                )
            )

            when (result) {
                is Result.Success -> {
                    result.value.let { idPar ->
                        packet.putValue(OPPGAVE_ID, idPar.oppgaveId.value)
                        idPar.fagsakId?.let { packet.putValue(FAGSAK_ID, it.value) }
                    }
                    packet.putValue(PacketKeys.FERDIGSTILT_ARENA, true)
                    logger.info { "Fornyet rettighet - laget oppgave" }
                }
                is Result.Failure -> logger.info { "Feilet opprettelse Fornyet rettighet oppgave" }
            }
        }
        return@instrument neste?.håndter(packet) ?: packet
    }

    override fun kanBehandle(packet: Packet): Boolean {
        return PacketMapper.hasNaturligIdent(packet) && PacketMapper.hasAktørId(packet) && packet.fornyetRettighet()
    }
}

internal class OppfyllerMinsteinntektBehandlingsChain(
    private val vilkårtester: Vilkårtester,
    neste: BehandlingsChain?
) : BehandlingsChain(neste) {
    override fun kanBehandle(packet: Packet) =
        PacketMapper.hasNaturligIdent(packet) &&
            PacketMapper.hasAktørId(packet) &&
            PacketMapper.harIkkeFagsakId(packet) &&
            PacketMapper.henvendelse(packet) == NyttSaksforhold &&
            !packet.hasField(PacketKeys.OPPFYLLER_MINSTEINNTEKT)

    override fun håndter(packet: Packet): Packet = instrument {
        if (kanBehandle(packet)) {
            try {
                val minsteArbeidsinntektVilkår =
                    vilkårtester.hentMinsteArbeidsinntektVilkår(PacketMapper.aktørFrom(packet).id)

                minsteArbeidsinntektVilkår?.let {
                    packet.putValue(PacketKeys.OPPFYLLER_MINSTEINNTEKT, it.harBeståttMinsteArbeidsinntektVilkår)
                    packet.putValue(PacketKeys.KORONAREGELVERK_MINSTEINNTEKT_BRUKT, it.koronaRegelverkBrukt)
                    inngangsvilkårResultatTellerInc(it.harBeståttMinsteArbeidsinntektVilkår)
                }
            } catch (e: Exception) {
                logger.warn(e) { "Kunne ikke vurdere minste arbeidsinntekt" }
            }
        }

        return@instrument neste?.håndter(packet) ?: packet
    }
}

internal class NyttSaksforholdBehandlingsChain(
    private val arena: ArenaClient,
    neste: BehandlingsChain?
) : BehandlingsChain(neste) {

    override fun kanBehandle(packet: Packet): Boolean =
        PacketMapper.hasNaturligIdent(packet) &&
            PacketMapper.harIkkeFagsakId(packet) &&
            PacketMapper.henvendelse(packet) == NyttSaksforhold &&
            !packet.hasField(PacketKeys.FERDIGSTILT_ARENA) &&
            arena.harIkkeAktivSak(PacketMapper.bruker(packet))
                .also {
                    if (!it) Metrics.automatiskJournalførtNeiTellerInc(
                        "aktiv_sak",
                        PacketMapper.tildeltEnhetsNrFrom(packet)
                    )
                }

    override fun håndter(packet: Packet): Packet = instrument {
        if (kanBehandle(packet)) {
            val tilleggsinformasjon =
                createArenaTilleggsinformasjon(
                    PacketMapper.dokumentTitlerFrom(packet),
                    PacketMapper.registrertDatoFrom(packet)
                )

            val oppgaveBenk = PacketMapper.oppgaveBeskrivelseOgBenk(packet)

            val result = arena.bestillOppgave(
                StartVedtakCommand(
                    naturligIdent = PacketMapper.bruker(packet).id,
                    behandlendeEnhetId = oppgaveBenk.id,
                    tilleggsinformasjon = tilleggsinformasjon,
                    registrertDato = PacketMapper.registrertDatoFrom(packet),
                    oppgavebeskrivelse = oppgaveBenk.beskrivelse
                )
            )

            when (result) {
                is Result.Success -> {
                    result.value.let { idPar ->
                        packet.putValue(OPPGAVE_ID, idPar.oppgaveId.value)
                        idPar.fagsakId?.let { packet.putValue(FAGSAK_ID, it.value) }
                    }
                    packet.putValue(PacketKeys.FERDIGSTILT_ARENA, true)
                }
            }
        }

        return@instrument neste?.håndter(packet) ?: packet
    }
}

internal class EksisterendeSaksForholdBehandlingsChain(private val arena: ArenaClient, neste: BehandlingsChain?) :
    BehandlingsChain(neste) {

    private val eksisterendeHenvendelsesTyper = setOf(
        KlageAnke,
        Utdanning,
        Etablering,
        Gjenopptak,
        Ettersendelse
    )

    override fun kanBehandle(packet: Packet): Boolean =
        eksisterendeHenvendelsesTyper.contains(PacketMapper.henvendelse(packet)) &&
            PacketMapper.hasNaturligIdent(packet) &&
            !packet.hasField(PacketKeys.FERDIGSTILT_ARENA)

    override fun håndter(packet: Packet): Packet = instrument {
        if (kanBehandle(packet)) {

            val tilleggsinformasjon =
                createArenaTilleggsinformasjon(
                    PacketMapper.dokumentTitlerFrom(packet),
                    PacketMapper.registrertDatoFrom(packet)
                )

            val henvendelse = PacketMapper.henvendelse(packet)
            val result = arena.bestillOppgave(
                VurderHenvendelseAngåendeEksisterendeSaksforholdCommand(
                    naturligIdent = PacketMapper.bruker(packet).id,
                    behandlendeEnhetId = PacketMapper.tildeltEnhetsNrFrom(packet),
                    tilleggsinformasjon = tilleggsinformasjon,
                    oppgavebeskrivelse = henvendelse.oppgavebeskrivelse,
                    registrertDato = PacketMapper.registrertDatoFrom(packet)
                )
            )

            when (result) {
                is Result.Success -> {
                    packet.putValue(PacketKeys.FERDIGSTILT_ARENA, true)
                }
            }
        }

        return@instrument neste?.håndter(packet) ?: packet
    }
}

internal class OppdaterJournalpostBehandlingsChain(val journalpostApi: JournalpostApi, neste: BehandlingsChain?) :
    BehandlingsChain(neste) {
    override fun håndter(packet: Packet): Packet = instrument {
        if (kanBehandle(packet)) {
            journalpostApi.oppdater(
                journalpostId = packet.getStringValue(PacketKeys.JOURNALPOST_ID),
                jp = oppdaterJournalpostPayloadFrom(
                    packet,
                    packet.getNullableStringValue(FAGSAK_ID)?.let { FagsakId(it) }
                )
            )
            logger.info { "Oppdatert journalpost" }
        }

        return@instrument neste?.håndter(packet) ?: packet
    }

    override fun kanBehandle(packet: Packet) = PacketMapper.hasNaturligIdent(packet)

    private fun oppdaterJournalpostPayloadFrom(packet: Packet, fagsakId: FagsakId?): OppdaterJournalpostPayload {
        return OppdaterJournalpostPayload(
            avsenderMottaker = PacketMapper.avsenderFrom(packet),
            bruker = PacketMapper.bruker(packet),
            tittel = PacketMapper.tittelFrom(packet),
            sak = PacketMapper.sakFrom(fagsakId),
            dokumenter = PacketMapper.dokumenterFrom(packet)
        )
    }
}

internal class FerdigstillJournalpostBehandlingsChain(
    val journalpostApi: JournalpostApi,
    neste: BehandlingsChain?
) : BehandlingsChain(neste) {
    override fun kanBehandle(packet: Packet) =
        packet.hasField(PacketKeys.FERDIGSTILT_ARENA)

    override fun håndter(packet: Packet): Packet = instrument {
        if (kanBehandle(packet)) {
            journalpostApi.ferdigstill(packet.getStringValue(PacketKeys.JOURNALPOST_ID))
            logger.info { "Automatisk journalført" }
        }

        return@instrument neste?.håndter(packet) ?: packet
    }
}

internal class ManuellJournalføringsBehandlingsChain(
    val manuellJournalføringsOppgaveClient: ManuellJournalføringsOppgaveClient,
    neste: BehandlingsChain?
) : BehandlingsChain(neste) {
    override fun kanBehandle(packet: Packet) =
        !packet.hasField(PacketKeys.FERDIGSTILT_ARENA)

    override fun håndter(packet: Packet): Packet = instrument {
        if (kanBehandle(packet)) {
            manuellJournalføringsOppgaveClient.opprettOppgave(
                PacketMapper.journalpostIdFrom(packet),
                PacketMapper.nullableAktørFrom(packet)?.id,
                PacketMapper.tittelFrom(packet),
                PacketMapper.tildeltEnhetsNrFrom(packet),
                PacketMapper.registrertDatoFrom(packet)
            )
            logger.info { "Manuelt journalført" }
        }

        return@instrument neste?.håndter(packet) ?: packet
    }
}

internal class MarkerFerdigBehandlingsChain(neste: BehandlingsChain?) : BehandlingsChain(neste) {
    override fun kanBehandle(packet: Packet) = true

    override fun håndter(packet: Packet): Packet = instrument {
        if (kanBehandle(packet)) {
            packet.putValue(PacketKeys.FERDIG_BEHANDLET, true)
            Metrics.jpFerdigStillInc()
        }

        return@instrument neste?.håndter(packet) ?: packet
    }
}

internal class StatistikkChain(neste: BehandlingsChain?) : BehandlingsChain(neste) {
    override fun kanBehandle(packet: Packet): Boolean = PacketMapper.henvendelse(packet) == NyttSaksforhold

    override fun håndter(packet: Packet): Packet = instrument {
        if (kanBehandle(packet)) {
            packet.andreYtelser()?.let { Metrics.andreYtelser.labels(it.toString()).inc() }
            packet.antallArbeidsforhold()?.let { Metrics.antallArbeidsforhold.labels(it.toString()).inc() }
            packet.arbeidstilstand()?.let { Metrics.arbeidstilstand.labels(it).inc() }
            packet.språk()?.let { Metrics.språk.labels(it).inc() }
            packet.utdanning()?.let { Metrics.utdanning.labels(it).inc() }
            packet.egenNæring()?.let { Metrics.egenNæring.labels(it.toString()).inc() }
            packet.gårdsbruk()?.let { Metrics.gårdsbruk.labels(it.toString()).inc() }
            packet.fangstOgFiske()?.let { Metrics.fangstOgFiske.labels(it.toString()).inc() }
            packet.harEøsArbeidsforhold().let { Metrics.jobbetieøs.labels(it.toString()).inc() }
            packet.reellArbeidssøker()?.let {
                Metrics.reellArbeidssøker.labels(
                    it.villigAlle.toString(),
                    it["villigdeltid"].toString(),
                    it["villigpendle"].toString(),
                    it["villighelse"].toString(),
                    it["villigjobb"].toString()
                ).inc()
            }
        }

        return@instrument neste?.håndter(packet) ?: packet
    }
}
