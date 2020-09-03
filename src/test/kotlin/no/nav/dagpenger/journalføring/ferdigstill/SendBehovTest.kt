package no.nav.dagpenger.journalføring.ferdigstill

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SendBehovTest {

    @Test
    fun `Skal hente svar fra medlemskapbehov-river `() {
        val medlemskapBehovRiver = MedlemskapBehovRiver()

        val resultat = medlemskapBehovRiver.hentSvar("12345678910", LocalDate.now(), "12345")

        resultat.resultat.svar shouldBe "JA"
    }
}
