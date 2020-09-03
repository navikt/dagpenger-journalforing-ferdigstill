package no.nav.dagpenger.journalføring.ferdigstill

import org.junit.jupiter.api.Test

class TestBehovRiver() : BehovRiver() {
    fun hentTestSvar(tull: String): String {
        val id = opprettBehov(mutableMapOf("tull" to "tull"))

        return hentSvar(id)["tull"].asText()
    }
}

class BehovRiverTest {

    @Test
    fun `Skal kunne opprette behov`() {
        val river = TestBehovRiver()

        river.hentTestSvar("tull")
    }
}
