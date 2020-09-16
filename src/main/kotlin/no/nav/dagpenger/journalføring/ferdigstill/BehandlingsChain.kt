package no.nav.dagpenger.journalføring.ferdigstill

import com.github.kittinunf.result.Result
import io.prometheus.client.Histogram
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.finn.unleash.Unleash
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.Metrics.inngangsvilkårResultatTellerInc
import no.nav.dagpenger.journalføring.ferdigstill.Metrics.medlemskapstatusTeller
import no.nav.dagpenger.journalføring.ferdigstill.Metrics.oppfyllerIkkeMinsteinnektMenOppfyllerMedlemskap
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.FAGSAK_ID
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.OPPGAVE_ID
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.JournalpostApi
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ManuellJournalføringsOppgaveClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.OppdaterJournalpostPayload
import no.nav.dagpenger.journalføring.ferdigstill.adapter.StartVedtakCommand
import no.nav.dagpenger.journalføring.ferdigstill.adapter.VurderHenvendelseAngåendeEksisterendeSaksforholdCommand
import no.nav.dagpenger.journalføring.ferdigstill.adapter.createArenaTilleggsinformasjon
import no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester.Vilkårtester
import java.time.LocalDate

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

internal class OppfyllerMinsteinntektBehandlingsChain(
    private val vilkårtester: Vilkårtester,
    private val medlemskapBehovRiver: MedlemskapBehovRiver,
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
                runBlocking(SupervisorJob() + IO) {
                    val minsteArbeidsinntektVilkår = async {
                        vilkårtester.hentMinsteArbeidsinntektVilkår(PacketMapper.aktørFrom(packet).id)
                    }

                    val medlemskap = async {
                        medlemskapBehovRiver.hentSvar(
                            fnr = packet.getStringValue(PacketKeys.NATURLIG_IDENT),
                            beregningsdato = LocalDate.now(),
                            journalpostId = PacketMapper.journalpostIdFrom(packet)
                        )
                    }

                    kotlin.runCatching { medlemskap.await() }
                        .onFailure {
                            logger.warn(it) { "Kunne ikke hente medlemskapstatus" }
                        }.onSuccess {
                            packet.putValue(PacketKeys.MEDLEMSKAP_STATUS, it)
                            medlemskapstatusTeller(it)
                        }

                    minsteArbeidsinntektVilkår.await()?.let {
                        packet.putValue(PacketKeys.OPPFYLLER_MINSTEINNTEKT, it.harBeståttMinsteArbeidsinntektVilkår)
                        packet.putValue(PacketKeys.KORONAREGELVERK_MINSTEINNTEKT_BRUKT, it.koronaRegelverkBrukt)
                        inngangsvilkårResultatTellerInc(it.harBeståttMinsteArbeidsinntektVilkår)
                    }
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
    val toggle: Unleash,
    neste: BehandlingsChain?
) : BehandlingsChain(neste) {

    override fun kanBehandle(packet: Packet): Boolean =
        PacketMapper.hasNaturligIdent(packet) &&
            PacketMapper.harIkkeFagsakId(packet) &&
            PacketMapper.henvendelse(packet) == NyttSaksforhold &&
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

            val kanAvslåsPåMinsteinntekt = packet.getNullableBoolean(PacketKeys.OPPFYLLER_MINSTEINNTEKT) == false
            val koronaRegelverkMinsteinntektBrukt =
                packet.getNullableBoolean(PacketKeys.KORONAREGELVERK_MINSTEINNTEKT_BRUKT) == true
            val medlemskapstatus = packet.getNullableStringValue(PacketKeys.MEDLEMSKAP_STATUS)?.let { Medlemskapstatus.valueOf(it) }
            medlemskapstatus?.let {
                when (it) {
                    Medlemskapstatus.JA -> oppfyllerIkkeMinsteinnektMenOppfyllerMedlemskap(kanAvslåsPåMinsteinntekt)
                    else -> oppfyllerIkkeMinsteinnektMenOppfyllerMedlemskap(false)
                }
            }

            val result = arena.bestillOppgave(
                StartVedtakCommand(
                    naturligIdent = PacketMapper.bruker(packet).id,
                    behandlendeEnhetId = finnBehandlendeEnhet(packet),
                    tilleggsinformasjon = tilleggsinformasjon,
                    registrertDato = PacketMapper.registrertDatoFrom(packet),
                    oppgavebeskrivelse = when {
                        kanAvslåsPåMinsteinntekt && koronaRegelverkMinsteinntektBrukt -> "Minsteinntekt - mulig avslag - korona\n"
                        kanAvslåsPåMinsteinntekt && !koronaRegelverkMinsteinntektBrukt -> "Minsteinntekt - mulig avslag\n"
                        else -> PacketMapper.henvendelse(packet).oppgavebeskrivelse
                    }
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

    private fun finnBehandlendeEnhet(
        packet: Packet
    ): String {
        if (!toggle.isEnabled("dagpenger-journalforing-ferdigstill.bruk_hurtig_enhet", false)) {
            return PacketMapper.tildeltEnhetsNrFrom(packet)
        }

        val kanAvslåsPåMinsteinntekt = packet.getNullableBoolean(PacketKeys.OPPFYLLER_MINSTEINNTEKT) == false

        return when (kanAvslåsPåMinsteinntekt) {
            true -> packet.finnEnhetForHurtigAvslag()
            false -> PacketMapper.tildeltEnhetsNrFrom(packet)
        }
    }

    private fun Packet.finnEnhetForHurtigAvslag() = when (this.getStringValue(PacketKeys.BEHANDLENDE_ENHET)) {
        "4450" -> ENHET_FOR_HURTIG_AVSLAG_IKKE_PERMITTERT
        "4455" -> ENHET_FOR_HURTIG_AVSLAG_PERMITTERT
        else -> this.getStringValue(PacketKeys.BEHANDLENDE_ENHET)
    }
}

internal class EksisterendeSaksForholdBehandlingsChain(private val arena: ArenaClient, neste: BehandlingsChain?) :
    BehandlingsChain(neste) {

    private val eksisterendeHenvendelsesTyper = setOf(
        KlageAnke,
        Utdanning,
        Etablering,
        Gjenopptak
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
