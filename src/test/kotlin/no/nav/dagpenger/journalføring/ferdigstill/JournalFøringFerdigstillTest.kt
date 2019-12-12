package no.nav.dagpenger.journalføring.ferdigstill

import io.kotlintest.matchers.doubles.shouldBeGreaterThan
import io.kotlintest.shouldBe
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyAll
import io.prometheus.client.CollectorRegistry
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.AKTØR_ID
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.ARENA_SAK_ID
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.ARENA_SAK_OPPRETTET
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.AVSENDER_NAVN
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.DOKUMENTER
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.FNR
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.JOURNALPOST_ID
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.dokumentJsonAdapter
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.journalPostFrom
import org.junit.jupiter.api.Test

internal class JournalFøringFerdigstillTest {

    private val journalPostApi = mockk<JournalPostApi>(relaxed = true)
    private val oppgaveClient = mockk<OppgaveClient>(relaxed = true)

    @Test
    fun `Filter is true when packet contains the required values`() {
        isJournalFørt.test("", Packet().apply {
            this.putValue(FNR, "fnr")
            this.putValue(JOURNALPOST_ID, "journalPostId")
            this.putValue(ARENA_SAK_OPPRETTET, true)
            this.putValue(DOKUMENTER, true)
            this.putValue(AVSENDER_NAVN, true)
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

        val journalFøringFerdigstill = JournalFøringFerdigstill(journalPostApi, mockk())
        val journalPostId = "journalPostId"

        val packet = Packet().apply {
            this.putValue(FNR, "fnr")
            this.putValue(JOURNALPOST_ID, journalPostId)
            this.putValue(ARENA_SAK_ID, "arenaSakId")
            this.putValue(AVSENDER_NAVN, "et navn")
            dokumentJsonAdapter.toJsonValue(listOf(Dokument("id1", "tittel1")))?.let { this.putValue(DOKUMENTER, it) }
        }

        journalFøringFerdigstill.handlePacket(packet)

        verifyAll {
            journalPostApi.ferdigstill(journalPostId)
            journalPostApi.oppdater(journalPostId, journalPostFrom(packet))
        }
    }

    @Test
    fun ` Opprett gosys-oppgave og ikke ferdigstill når det ikke finnes fagsak `() {

        val journalFøringFerdigstill = JournalFøringFerdigstill(journalPostApi, oppgaveClient)
        val journalPostId = "journalPostId"
        val aktørId = "12345678910"

        val packet = Packet().apply {
            this.putValue(FNR, "fnr")
            this.putValue(JOURNALPOST_ID, journalPostId)
            this.putValue(AVSENDER_NAVN, "et navn")
            this.putValue(AKTØR_ID, aktørId)
            dokumentJsonAdapter.toJsonValue(listOf(Dokument("id1", "tittel1")))?.let { this.putValue(DOKUMENTER, it) }
        }

        journalFøringFerdigstill.handlePacket(packet)

        verifyAll {
            oppgaveClient.opprettOppgave(journalPostId, aktørId)
            journalPostApi.oppdater(journalPostId, journalPostFrom(packet))
        }

        verify(exactly = 0) {
            journalPostApi.ferdigstill(any())
        }
    }

    @Test
    fun `Metrikker blir oppdatert når journal poster blir ferdigstilt`() {

        JournalFøringFerdigstill(journalPostApi, oppgaveClient).apply {
            val generellPacket = Packet().apply {
                this.putValue(FNR, "fnr")
                this.putValue(AKTØR_ID, "aktør")
                this.putValue(JOURNALPOST_ID, "journalPostId")
                this.putValue(AVSENDER_NAVN, "et navn")
                dokumentJsonAdapter.toJsonValue(listOf(Dokument("id1", "tittel1")))?.let { this.putValue(DOKUMENTER, it) }
            }
            this.handlePacket(generellPacket)

            val fagsakPacket = Packet().apply {
                this.putValue(FNR, "fnr")
                this.putValue(AKTØR_ID, "aktør")
                this.putValue(JOURNALPOST_ID, "journalPostId")
                this.putValue(ARENA_SAK_ID, "arenaSakId")
                this.putValue(AVSENDER_NAVN, "et navn")
                dokumentJsonAdapter.toJsonValue(listOf(Dokument("id1", "tittel1")))?.let { this.putValue(DOKUMENTER, it) }
            }
            this.handlePacket(fagsakPacket)
        }

        CollectorRegistry.defaultRegistry.getSampleValue("dagpenger_journalpost_ferdigstilt", arrayOf("saksType"), arrayOf(SaksType.FAGSAK.name.toLowerCase())) shouldBeGreaterThan 0.0
        CollectorRegistry.defaultRegistry.getSampleValue("dagpenger_journalpost_ferdigstilt", arrayOf("saksType"), arrayOf(SaksType.GENERELL_SAK.name.toLowerCase())) shouldBeGreaterThan 0.0
    }
}
