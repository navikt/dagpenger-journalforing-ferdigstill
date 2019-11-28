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
    fun `Ferdigstill Fagsak når Packet inneholder Arena sak id `() {

        val journalFøringFerdigstill = JournalFøringFerdigstill(journalPostApi)
        val journalPostId = "journalPostId"

        val packet = Packet().apply {
            this.putValue(FNR, "fnr")
            this.putValue(JOURNALPOST_ID, journalPostId)
            this.putValue(ARENA_SAK_ID, "arenaSakId")
        }

        journalFøringFerdigstill.handlePacket(packet)

        verifyAll {
            journalPostApi.ferdigstill(journalPostId)
            journalPostApi.oppdater(journalPostId, ArenaSak(packet))
        }
    }

    @Test
    fun `Ferdigstill Generell Sak når Packet ikke inneholder Arena sak id `() {

        val journalFøringFerdigstill = JournalFøringFerdigstill(journalPostApi)
        val journalPostId = "journalPostId"

        val packet = Packet().apply {
            this.putValue(FNR, "fnr")
            this.putValue(JOURNALPOST_ID, journalPostId)
        }

        journalFøringFerdigstill.handlePacket(packet)

        verifyAll {
            journalPostApi.ferdigstill(journalPostId)
            journalPostApi.oppdater(journalPostId, GenerellSak(packet))
        }
    }

    @Test
    fun `Serialisering av Arena sak til json`() {
        val arenaSak = ArenaSak(Packet().apply {
            this.putValue(FNR, "fnr")
            this.putValue(ARENA_SAK_ID, "arenaSakId")
        })

        arenaSak.toJsonString().trimIndent() shouldBe """
            {
               "bruker": {
                "id": "fnr",
                  "idType": "FNR"
                },
                "behandlingstema": "ab0001",
                "tema": "DAG",
                "journalfoerendeEnhet": "9999",
                "sak": {
                   "sakstype": "FAGSAK",
                   "fagsaksystem": "AO01",
                   "fagsakId": "arenaSakId"
                 }
            }
        """.trimIndent()
    }

    @Test
    fun `Serialisering av Generell sak til json`() {
        val generellSak = GenerellSak(Packet().apply {
            this.putValue(FNR, "fnr")
        })

        generellSak.toJsonString().trimIndent() shouldBe """
            {
               "bruker": {
                "id": "fnr",
                  "idType": "FNR"
                },
                "behandlingstema": "ab0001",
                "tema": "DAG",
                "journalfoerendeEnhet": "9999",
                "sak": {
                   "sakstype": "GENERELL_SAK"
                 }
            }
        """.trimIndent()
    }
}
