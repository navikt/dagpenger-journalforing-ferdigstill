package no.nav.dagpenger.journalføring.ferdigstill

import mu.KotlinLogging
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.FAGSAK_ID
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaSak
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaSakStatus
import no.nav.dagpenger.journalføring.ferdigstill.adapter.OppgaveCommand
import no.nav.dagpenger.journalføring.ferdigstill.adapter.StartVedtakCommand
import no.nav.dagpenger.journalføring.ferdigstill.adapter.VurderHenvendelseAngåendeEksisterendeSaksforholdCommand
import no.nav.dagpenger.journalføring.ferdigstill.adapter.createArenaTilleggsinformasjon
import no.nav.dagpenger.streams.HealthStatus

// GoF pattern - Chain of responsibility (https://en.wikipedia.org/wiki/Chain-of-responsibility_pattern)
abstract class Behandlingslenke(protected val neste: Behandlingslenke? = null) {
    abstract fun håndter(packet: Packet): Packet
    abstract fun kanBehandle(packet: Packet): Boolean
}

internal class NySøknadIArenaBehandlingslenke(private val arena: ArenaClient, neste: Behandlingslenke?) :
    Behandlingslenke(neste) {
    private val logger = KotlinLogging.logger {}

    override fun kanBehandle(packet: Packet): Boolean =
        PacketMapper.hasNaturligIdent(packet)
            && PacketMapper.harIkkeFagsakId(packet)
            && PacketMapper.henvendelse(packet) == NyttSaksforhold
            && arena.harIkkeAktivSak(PacketMapper.bruker(packet))

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

                ),
                PacketMapper.journalpostIdFrom(packet)
            )

            if (fagsakId != null) {
                packet.putValue(FAGSAK_ID, fagsakId.value)
            }
        }
        return neste?.håndter(packet) ?: packet
    }
}

internal class EksisterendeSaksForholdBehandlingslenke(private val arena: ArenaClient, neste: Behandlingslenke?) :
    Behandlingslenke(null) {

    private val eksisterendeHenvendelsesTyper = setOf(
        KlageAnke, Utdanning, Etablering, Gjenopptak
    )

    override fun kanBehandle(packet: Packet): Boolean =
        eksisterendeHenvendelsesTyper.contains(PacketMapper.henvendelse(packet))
            && PacketMapper.hasNaturligIdent(packet)
            && !packet.hasField(PacketKeys.FERDIGSTILT_ARENA)

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

