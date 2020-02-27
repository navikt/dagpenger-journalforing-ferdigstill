package no.nav.dagpenger.journalføring.ferdigstill

import mu.KotlinLogging
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.FAGSAK_ID
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaSak
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaSakStatus
import no.nav.dagpenger.journalføring.ferdigstill.adapter.OppgaveCommand
import no.nav.dagpenger.journalføring.ferdigstill.adapter.StartVedtakCommand
import no.nav.dagpenger.journalføring.ferdigstill.adapter.createArenaTilleggsinformasjon
import no.nav.dagpenger.streams.HealthStatus

// GoF pattern - Chain of responsibility (https://en.wikipedia.org/wiki/Chain-of-responsibility_pattern)
abstract class BehandlingsChain(val neste: BehandlingsChain? = null) {
    abstract fun håndter(packet: Packet): Packet
}

internal class NyArenaSakBehandlingskjede(val arena: ArenaClient, neste: BehandlingsChain?) : BehandlingsChain(neste) {
    private val logger = KotlinLogging.logger {}

    private fun kanbehandle(packet: Packet) =
        PacketToJoarkPayloadMapper.hasNaturligIdent(packet)
            && PacketToJoarkPayloadMapper.harIkkeFagsakId(packet)
            && PacketToJoarkPayloadMapper.henvendelse(packet) == NyttSaksforhold
            && arena.harIkkeAktivSak(PacketToJoarkPayloadMapper.bruker(packet))

    override fun håndter(packet: Packet): Packet {
        return if (kanbehandle(packet)) {
            val tilleggsinformasjon =
                createArenaTilleggsinformasjon(
                    PacketToJoarkPayloadMapper.dokumentTitlerFrom(packet),
                    PacketToJoarkPayloadMapper.registrertDatoFrom(packet)
                )

            val fagsakId: FagsakId? = arena.bestillOppgave(
                StartVedtakCommand(
                    naturligIdent = PacketToJoarkPayloadMapper.bruker(packet).id,
                    behandlendeEnhetId = PacketToJoarkPayloadMapper.tildeltEnhetsNrFrom(packet),
                    tilleggsinformasjon = tilleggsinformasjon,
                    registrertDato = PacketToJoarkPayloadMapper.registrertDatoFrom(packet)
                ),
                PacketToJoarkPayloadMapper.journalpostIdFrom(packet)
            )

            if (fagsakId != null) {
                packet.putValue(FAGSAK_ID, fagsakId.value)
            }

            return packet
        } else {
            neste?.håndter(packet) ?: packet
        }
    }
}

fun main() {
    val t = object : BehandlingsChain() {
        override fun håndter(packet: Packet): Packet {
            return packet
        }
    }
    val c = object : ArenaClient {
        override fun bestillOppgave(command: OppgaveCommand): FagsakId? {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun hentArenaSaker(naturligIdent: String): List<ArenaSak> {
            println("evaluarer")
            return listOf(ArenaSak(1111, ArenaSakStatus.Inaktiv))
        }

        override fun status(): HealthStatus {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }
    }
    val kjede = NyArenaSakBehandlingskjede(neste = t, arena = c)
    kjede.håndter(
        Packet().apply {
            putValue("naturligIdent", "1234")
            putValue("henvendelsestype", "NY_SØKNAD")
        }
    )
}
