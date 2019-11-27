package no.nav.dagpenger.journalføring.ferdigstill

import io.kotlintest.shouldBe
import io.mockk.mockk
import io.mockk.verifyAll
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.ARENA_SAK_ID
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.ARENA_SAK_OPPRETTET
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.FNR
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.JOURNALPOST_ID
import org.junit.jupiter.api.Test

internal class JournalFøringFerdigstillTest {

    private val journalPostApi = mockk<JournalPostApi>(relaxed = true)

    @Test
    fun `Filter is true when packet contains the required values`() {
        isJournalFørt.test("", Packet().apply {
            this.putValue(FNR, "fnr")
            this.putValue(JOURNALPOST_ID, "journalPostId")
            this.putValue(ARENA_SAK_ID, "arenaSakid")
            this.putValue(ARENA_SAK_OPPRETTET, true)
        }) shouldBe true
    }

    @Test
    fun `Filter is false when packet does not contain the required values`() {
        isJournalFørt.test("", Packet().apply {
            this.putValue(FNR, "fnr")
            this.putValue(JOURNALPOST_ID, "journalPostId")
            this.putValue(ARENA_SAK_ID, "arenaSakid")
        }) shouldBe false
    }

    @Test
    fun `Extract correct Packet values on calls to Api`() {

        val journalFøringFerdigstill = JournalFøringFerdigstill(journalPostApi)
        val journalPostId = "journalpostid"
        val fnr = "fødselnummer"
        val arenaSakId = "arenaSakId"
        val json = """{
  "bruker": {
    "id": "$fnr",
    "idType": "FNR"
  },
  "tema": "DAG",
  "behandlingstema": "ab0001",
  "journalfoerendeEnhet": "9999",
  "sak": {
    "sakstype": "FAGSAK",
    "fagsaksystem": "AO01",
    "fagsakId": "$arenaSakId"
  }
}""".trimIndent()

        journalFøringFerdigstill.handlePacket(Packet().apply {
            this.putValue(FNR, fnr)
            this.putValue(JOURNALPOST_ID, journalPostId)
            this.putValue(ARENA_SAK_ID, arenaSakId)
        })

        verifyAll {
            journalPostApi.ferdigstill(journalPostId)
            journalPostApi.oppdater(journalPostId, json)
        }
    }
}
