package no.nav.dagpenger.journalføring.ferdigstill

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaClient
import org.junit.Test

internal class NyttSaksforholdBehandlingsChainTest {

    @Test
    fun `Skal ikke behandle pakker uten person identifikator`() {

        val arenaMock: ArenaClient = mockk()
        val nesteKjede: BehandlingsChain = mockk()

        val nyArenaSakKjede = NyttSaksforholdBehandlingsChain(
            arena = arenaMock,
            neste = nesteKjede
        )
        val packet = Packet().apply {
            this.putValue(PacketKeys.HENVENDELSESTYPE, "NY_SØKNAD")
        }
        every { nesteKjede.håndter(any()) } returns packet
        nyArenaSakKjede.håndter(packet)

        verify(exactly = 1) { nesteKjede.håndter(any()) }
    }
}
