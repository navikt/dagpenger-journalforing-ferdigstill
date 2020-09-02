package no.nav.dagpenger.journalføring.ferdigstill

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.dagpenger.events.Packet
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class HentMedlemskapChainTest {

    @Test
    fun `Hent medlemskap basert på fnr`() {

        val medlemskapOppslagClient: MedlemskapOppslagClient = mockk()
        val hentMedlemskapChain = HentMedlemskapChain(medlemskapOppslagClient = medlemskapOppslagClient, neste = null)
        val fnr = "12345678919"
        val packet = Packet().apply {
            this.putValue(PacketKeys.NATURLIG_IDENT, fnr)
        }

        coEvery { medlemskapOppslagClient.hentMedlemskapsevaluering(fnr, any(), any(), any(), any()) } returns
                Medlemskapsevaluering(LocalDateTime.now(), "", "", emptyMap(), Medlemskapresultat("", "", "", "JA", emptyList()))
        hentMedlemskapChain.håndter(packet)
        packet.hasField(PacketKeys.MEDLEMSKAP) shouldBe true
        packet.getBoolean(PacketKeys.MEDLEMSKAP) shouldBe true
    }
}
