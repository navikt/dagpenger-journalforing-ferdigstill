package no.nav.dagpenger.journalføring.ferdigstill

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import no.nav.dagpenger.events.Packet
import org.junit.jupiter.api.Test

class HentMedlemskapChainTest {

    @Test
    fun `Hent medlemskap basert på fnr`() {


        val medlemskapOppslagClient: MedlemskapOppslagClient = mockk()
        val hentMedlemskapChain = HentMedlemskapChain(medlemskapOppslagClient = medlemskapOppslagClient, neste = null)


        val packet = Packet().apply {
            this.putValue(PacketKeys.NATURLIG_IDENT, "12345678919")
        }

        hentMedlemskapChain.håndter(packet)
        packet.hasField(PacketKeys.MEDLEMSKAP) shouldBe true
        packet.getBoolean(PacketKeys.MEDLEMSKAP) shouldBe true
    }
}
