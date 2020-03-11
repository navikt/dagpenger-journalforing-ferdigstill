package no.nav.dagpenger.journalføring.ferdigstill

import mu.KotlinLogging
import no.finn.unleash.Unleash
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.Metrics.inngangsvilkårResultatTellerInc
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.FAGSAK_ID
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.JournalpostApi
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ManuellJournalføringsOppgaveClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.OppdaterJournalpostPayload
import no.nav.dagpenger.journalføring.ferdigstill.adapter.StartVedtakCommand
import no.nav.dagpenger.journalføring.ferdigstill.adapter.VurderHenvendelseAngåendeEksisterendeSaksforholdCommand
import no.nav.dagpenger.journalføring.ferdigstill.adapter.createArenaTilleggsinformasjon
import no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester.Vilkårtester

private val logger = KotlinLogging.logger {}

const val ENHET_FOR_HURTIGE_AVSLAG = "4403"

// GoF pattern - Chain of responsibility (https://en.wikipedia.org/wiki/Chain-of-responsibility_pattern)
abstract class Behandlingslenke(protected val neste: Behandlingslenke? = null) {
    abstract fun håndter(packet: Packet): Packet
    abstract fun kanBehandle(packet: Packet): Boolean
}

internal class OppfyllerMinsteinntektBehandlingsLenke(
    private val vilkårtester: Vilkårtester,
    private val toggle: Unleash,
    neste: Behandlingslenke?
) : Behandlingslenke(neste) {
    override fun kanBehandle(packet: Packet) =
        toggle.isEnabled("dagpenger-journalforing-ferdigstill.vilkaartesting") &&
            PacketMapper.hasNaturligIdent(packet) &&
            PacketMapper.hasAktørId(packet) &&
            PacketMapper.harIkkeFagsakId(packet) &&
            PacketMapper.henvendelse(packet) == NyttSaksforhold

    override fun håndter(packet: Packet): Packet {
        if (kanBehandle(packet)) {
            try {
                val oppfyllerMinsteinntekt =
                    vilkårtester.harBeståttMinsteArbeidsinntektVilkår(PacketMapper.aktørFrom(packet).id)

                oppfyllerMinsteinntekt?.let {
                    packet.putValue(PacketKeys.OPPFYLLER_MINSTEINNTEKT, it)
                    inngangsvilkårResultatTellerInc(it)
                }
            } catch (e: Exception) {
                logger.warn(e) { "Kunne ikke vurdere minste arbeidsinntekt" }
            }
        }
        return neste?.håndter(packet) ?: packet
    }
}

internal class NyttSaksforholdBehandlingslenke(
    private val arena: ArenaClient,
    val toggle: Unleash,
    neste: Behandlingslenke?
) :
    Behandlingslenke(neste) {

    override fun kanBehandle(packet: Packet): Boolean =
        PacketMapper.hasNaturligIdent(packet) &&
            PacketMapper.harIkkeFagsakId(packet) &&
            PacketMapper.henvendelse(packet) == NyttSaksforhold &&
            arena.harIkkeAktivSak(PacketMapper.bruker(packet))

    override fun håndter(packet: Packet): Packet {
        if (kanBehandle(packet)) {
            val tilleggsinformasjon =
                createArenaTilleggsinformasjon(
                    PacketMapper.dokumentTitlerFrom(packet),
                    PacketMapper.registrertDatoFrom(packet)
                )

            val kanAvslåsPåMinsteinntekt = packet.getNullableBoolean(PacketKeys.OPPFYLLER_MINSTEINNTEKT) == false

            val fagsakId: FagsakId? = arena.bestillOppgave(
                StartVedtakCommand(
                    naturligIdent = PacketMapper.bruker(packet).id,
                    behandlendeEnhetId = finnBehandlendeEnhet(kanAvslåsPåMinsteinntekt, packet),
                    tilleggsinformasjon = tilleggsinformasjon,
                    registrertDato = PacketMapper.registrertDatoFrom(packet),
                    oppgavebeskrivelse = when (kanAvslåsPåMinsteinntekt) {
                        true -> "Minsteinntekt - mulig avslag\n"
                        false -> PacketMapper.henvendelse(packet).oppgavebeskrivelse
                    }
                )
            )

            if (fagsakId != null) {
                packet.putValue(FAGSAK_ID, fagsakId.value)
            }
            packet.putValue(PacketKeys.FERDIGSTILT_ARENA, true)
        }
        return neste?.håndter(packet) ?: packet
    }

    private fun finnBehandlendeEnhet(
        kanAvslåsPåMinsteinntekt: Boolean,
        packet: Packet
    ): String {
        if (!toggle.isEnabled("dagpenger-journalforing-ferdigstill.bruk_hurtig_enhet", false)) {
            return PacketMapper.tildeltEnhetsNrFrom(packet)
        }

        return when (kanAvslåsPåMinsteinntekt) {
            true -> ENHET_FOR_HURTIGE_AVSLAG
            false -> PacketMapper.tildeltEnhetsNrFrom(packet)
        }
    }
}

internal class EksisterendeSaksForholdBehandlingslenke(private val arena: ArenaClient, neste: Behandlingslenke?) :
    Behandlingslenke(neste) {

    private val eksisterendeHenvendelsesTyper = setOf(
        KlageAnke, Utdanning, Etablering, Gjenopptak
    )

    override fun kanBehandle(packet: Packet): Boolean =
        eksisterendeHenvendelsesTyper.contains(PacketMapper.henvendelse(packet)) &&
            PacketMapper.hasNaturligIdent(packet) &&
            !packet.hasField(PacketKeys.FERDIGSTILT_ARENA)

    override fun håndter(packet: Packet): Packet {
        if (kanBehandle(packet)) {

            val tilleggsinformasjon =
                createArenaTilleggsinformasjon(
                    PacketMapper.dokumentTitlerFrom(packet),
                    PacketMapper.registrertDatoFrom(packet)
                )

            val henvendelse = PacketMapper.henvendelse(packet)
            arena.bestillOppgave(
                VurderHenvendelseAngåendeEksisterendeSaksforholdCommand(
                    naturligIdent = PacketMapper.bruker(packet).id,
                    behandlendeEnhetId = PacketMapper.tildeltEnhetsNrFrom(packet),
                    tilleggsinformasjon = tilleggsinformasjon,
                    oppgavebeskrivelse = henvendelse.oppgavebeskrivelse,
                    registrertDato = PacketMapper.registrertDatoFrom(packet)
                )
            )

            packet.putValue(PacketKeys.FERDIGSTILT_ARENA, true)
        }
        return neste?.håndter(packet) ?: packet
    }
}

internal class OppdaterJournalpostBehandlingslenke(val journalpostApi: JournalpostApi, neste: Behandlingslenke?) :
    Behandlingslenke(neste) {
    override fun håndter(packet: Packet): Packet {
        if (kanBehandle(packet)) {
            journalpostApi.oppdater(
                journalpostId = packet.getStringValue(PacketKeys.JOURNALPOST_ID),
                jp = oppdaterJournalpostPayloadFrom(
                    packet,
                    packet.getNullableStringValue(FAGSAK_ID)?.let { FagsakId(it) })
            )
            logger.info { "Oppdatert journalpost" }
        }
        return neste?.håndter(packet) ?: packet
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

internal class FerdigstillJournalpostBehandlingslenke(
    val journalpostApi: JournalpostApi,
    neste: Behandlingslenke?
) : Behandlingslenke(neste) {
    override fun kanBehandle(packet: Packet) =
        packet.hasField(PacketKeys.FERDIGSTILT_ARENA)

    override fun håndter(packet: Packet): Packet {
        if (kanBehandle(packet)) {
            journalpostApi.ferdigstill(packet.getStringValue(PacketKeys.JOURNALPOST_ID))
            logger.info { "Automatisk journalført" }
            Metrics.automatiskJournalførtJaTellerInc()
        }
        return neste?.håndter(packet) ?: packet
    }
}

internal class ManuellJournalføringsBehandlingslenke(
    val manuellJournalføringsOppgaveClient: ManuellJournalføringsOppgaveClient,
    neste: Behandlingslenke?
) : Behandlingslenke(neste) {
    override fun kanBehandle(packet: Packet) =
        !packet.hasField(PacketKeys.FERDIGSTILT_ARENA)

    override fun håndter(packet: Packet): Packet {
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
        return neste?.håndter(packet) ?: packet
    }
}

internal class MarkerFerdigBehandlingslenke(neste: Behandlingslenke?) : Behandlingslenke(neste) {
    override fun kanBehandle(packet: Packet) = true

    override fun håndter(packet: Packet): Packet {
        if (kanBehandle(packet)) {
            packet.putValue(PacketKeys.FERDIG_BEHANDLET, true)
            Metrics.jpFerdigStillInc()
        }
        return neste?.håndter(packet) ?: packet
    }
}
