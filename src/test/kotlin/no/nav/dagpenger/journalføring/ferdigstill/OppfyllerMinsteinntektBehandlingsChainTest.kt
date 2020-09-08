package no.nav.dagpenger.journalføring.ferdigstill

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester.Vilkårtester
import org.junit.Test

class OppfyllerMinsteinntektBehandlingsChainTest {
    @Test
    fun `Skal hente medlemskapstatus`() {
        val vilkårTesterMockk: Vilkårtester = mockk(relaxed = true)
        val medlemskapBehovRiverMockk: MedlemskapBehovRiver = mockk()
        val chain = OppfyllerMinsteinntektBehandlingsChain(
            vilkårtester = vilkårTesterMockk,
            medlemskapBehovRiver = medlemskapBehovRiverMockk,
            neste = null
        )

        coEvery {
            medlemskapBehovRiverMockk.hentSvar("fnr", any(), "12345")
        } returns Medlemskapstatus.JA

        val packet = Packet().apply {
            this.putValue(PacketKeys.AKTØR_ID, "aktørId")
            this.putValue(PacketKeys.NATURLIG_IDENT, "fnr")
            this.putValue(PacketKeys.HENVENDELSESTYPE, "NY_SØKNAD")
            this.putValue(PacketKeys.JOURNALPOST_ID, "12345")
        }

        val resultat = chain.håndter(packet)

        resultat.hasField("medlemskapStatus") shouldBe true
    }
}
