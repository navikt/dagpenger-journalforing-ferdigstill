package no.nav.dagpenger.journalføring.ferdigstill

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration.ofMillis
import java.time.Duration.ofSeconds

class BehovRiverTest {

    private val rapid = TestRapid()

    @Test
    fun `Skal kunne opprette behov`() = runBlocking {
        val river = BehovRiver(rapid, listOf(Behov.Medlemskap))
        val id = river.opprettBehov(mutableMapOf("tull" to "noe"))

        rapid.sendTestMessage(json(id))

        val noe = river.hentSvar(id) {
            message: JsonMessage ->
            message["@løsning"]["Medlemskap"]["tull"].asText()
        }

        noe shouldBe "bla"
    }

    @Test
    fun `Kaste NoSuchElementException der svar ikke kommer`() {
        val river = BehovRiver(rapid, listOf(Behov.Medlemskap), 3, ofMillis(2))
        val id = river.opprettBehov(mutableMapOf("tull" to "noe"))

        assertThrows<NoSuchElementException> {
            runBlocking {
                river.hentSvar(id) { message: JsonMessage ->
                    message["@løsning"]["Medlemskap"]["tull"].asText()
                }
            }
        }
    }

    @Test
    fun `Returnere svar der svaret er forsinket`() = runBlocking {
        val river = BehovRiver(rapid, listOf(Behov.Medlemskap), 3, ofSeconds(2))
        val id = river.opprettBehov(mutableMapOf("tull" to "noe"))

        val s = async {
            river.hentSvar(id) { message: JsonMessage ->
                message["@løsning"]["Medlemskap"]["tull"].asText()
            }
        }

        delay(1000)

        rapid.sendTestMessage(json(id))

        s.await() shouldBe "bla"
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
