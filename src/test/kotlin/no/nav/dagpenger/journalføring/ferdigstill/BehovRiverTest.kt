package no.nav.dagpenger.journalføring.ferdigstill

import io.kotest.matchers.shouldBe
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

class BehovRiverTest {

    private val rapid = TestRapid()

    /*
    class TestBehovRiver(private val rapidsConnection: TestRapid) : BehovRiver(rapidsConnection, listOf(Behov.Medlemskap)) {
        fun hentTestSvar(tull: String): String {
            val id = opprettBehov(mutableMapOf("tull" to tull))
            rapidsConnection.sendTestMessage(json(ULID().nextULID()))
            rapidsConnection.sendTestMessage(json(ULID().nextULID()))
            rapidsConnection.sendTestMessage(json(ULID().nextULID()))
            rapidsConnection.sendTestMessage(json(ULID().nextULID()))
            rapidsConnection.sendTestMessage(json(id))
            return hentSvar(id)["@løsning"]["Medlemskap"]["tull"].asText()
        }
    }

    @Test
    fun `Skal kunne opprette behov`() {
        val river = TestBehovRiver(rapid)

        river.hentTestSvar("tull") shouldBe "bla"
    }
     */

    @Test
    fun `Skal kunne opprette behov`() {
        val river = BehovRiver(rapid, listOf(Behov.Medlemskap)) {
            message: JsonMessage ->
            message["@løsning"]["Medlemskap"]["tull"].asText()
        }
        val id = river.opprettBehov(mutableMapOf("tull" to "noe"))

        rapid.sendTestMessage(json(id))

        val noe = river.hentSvar(id)

        noe shouldBe "bla"
    }
}

@Language("JSON")
private fun json(id: String): String =
    """{
  "@id": "$id",
  "@opprettet": "2020-04-17T14:00:12.795017",
  "@event_name": "behov",
  "@behov": [
    "Medlemskap"
  ],
  "aktørId": "01E6405C4PRMEQWT4CYW0ZQ3YN",
  "fødselsnummer": "01E6405C4P9X24GEC1E4GM0X1D",
  "vedtakId": "01E6405C4P9X24GEC1E4GM0X1D",
  "@løsning": {
    "Medlemskap": {
      "tull": "bla"
    }
  },
  "@final": true,
  "@besvart": "2020-04-17T14:00:12.795055"
}
"""
