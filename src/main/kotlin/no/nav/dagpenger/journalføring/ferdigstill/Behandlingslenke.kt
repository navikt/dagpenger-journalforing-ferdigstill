package no.nav.dagpenger.journalføring.ferdigstill

import mu.KotlinLogging
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.FAGSAK_ID
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.JournalpostApi
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ManuellJournalføringsOppgaveClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.OppdaterJournalpostPayload
import no.nav.dagpenger.journalføring.ferdigstill.adapter.StartVedtakCommand
import no.nav.dagpenger.journalføring.ferdigstill.adapter.VurderHenvendelseAngåendeEksisterendeSaksforholdCommand
import no.nav.dagpenger.journalføring.ferdigstill.adapter.createArenaTilleggsinformasjon

// GoF pattern - Chain of responsibility (https://en.wikipedia.org/wiki/Chain-of-responsibility_pattern)
abstract class Behandlingslenke(protected val neste: Behandlingslenke? = null) {
    abstract fun håndter(packet: Packet): Packet
    abstract fun kanBehandle(packet: Packet): Boolean
}

internal class NyttSaksforholdBehandlingslenke(private val arena: ArenaClient, neste: Behandlingslenke?) :
    Behandlingslenke(neste) {
    private val logger = KotlinLogging.logger {}

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

            val fagsakId: FagsakId? = arena.bestillOppgave(
                StartVedtakCommand(
                    naturligIdent = PacketMapper.bruker(packet).id,
                    behandlendeEnhetId = PacketMapper.tildeltEnhetsNrFrom(packet),
                    tilleggsinformasjon = tilleggsinformasjon,
                    registrertDato = PacketMapper.registrertDatoFrom(packet),
                    oppgavebeskrivelse = PacketMapper.henvendelse(packet).oppgavebeskrivelse

                )
            )

            if (fagsakId != null) {
                packet.putValue(FAGSAK_ID, fagsakId.value)
            }
            packet.putValue(PacketKeys.FERDIGSTILT_ARENA, true)
        }
        return neste?.håndter(packet) ?: packet
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
        }
        return neste?.håndter(packet) ?: packet
    }
}

internal class MarkerFerdigBehandlingslenke(neste: Behandlingslenke?) : Behandlingslenke(neste) {
    override fun kanBehandle(packet: Packet) = true

    override fun håndter(packet: Packet): Packet {
        if (kanBehandle(packet)) {
            packet.putValue(PacketKeys.FERDIG_BEHANDLET, true)
        }
        return neste?.håndter(packet) ?: packet
    }
}