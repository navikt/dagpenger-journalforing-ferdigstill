package no.nav.dagpenger.journalføring.ferdigstill

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.events.Packet
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class HentMedlemskapChainTest {

    @Test
    fun `Hent medlemskap basert på fnr`() {

        val hentMedlemskapChain = HentMedlemskapChain(null)

        val packet = Packet().apply {
            this.putValue(PacketKeys.NATURLIG_IDENT, "12345678919")
        }

        hentMedlemskapChain.håndter(packet)
        packet.hasField(PacketKeys.MEDLEMSKAP) shouldBe true
        packet.getBoolean(PacketKeys.MEDLEMSKAP) shouldBe true
    }
}
