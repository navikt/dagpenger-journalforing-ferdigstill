package no.nav.dagpenger.journalføring.ferdigstill

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SendBehovTest {

    @Test
    fun `Skal sende behov på behov river `() {
        val medlemskapBehovRiver = MedlemskapBehovRiver()

        val resultat = medlemskapBehovRiver.opprettBehov("TODO")

        resultat.resultat.svar shouldBe "JA"
    }
}
