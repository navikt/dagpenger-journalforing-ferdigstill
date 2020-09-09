package no.nav.dagpenger.journalføring.ferdigstill

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SendBehovTest {

    @Test
    fun `Skal hente svar fra medlemskapbehov-river `() = runBlocking {
        val behovRiver: BehovRiver = mockk()

        every {
            behovRiver.opprettBehov(any())
        } returns "12345"

        coEvery {
            behovRiver.hentSvar<Medlemskapstatus>("12345", any())
        } returns Medlemskapstatus.JA

        val medlemskapBehovRiver = MedlemskapBehovRiver(behovRiver)

        val resultat = medlemskapBehovRiver.hentSvar("12345678910", LocalDate.now(), "12345")

        resultat shouldBe Medlemskapstatus.JA
    }
}
