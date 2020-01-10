package no.nav.dagpenger.journalføring.ferdigstill

import io.kotlintest.matchers.doubles.shouldBeGreaterThan
import io.kotlintest.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyAll
import io.prometheus.client.CollectorRegistry
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.arena.adapter.ArenaClient
import no.nav.dagpenger.journalføring.arena.adapter.ArenaSak
import no.nav.dagpenger.journalføring.arena.adapter.ArenaSakStatus
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.AKTØR_ID
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.ARENA_SAK_ID
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.ARENA_SAK_OPPRETTET
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.AVSENDER_NAVN
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.BEHANDLENDE_ENHET
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.DATO_REGISTRERT
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.DOKUMENTER
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.NATURLIG_IDENT
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.JOURNALPOST_ID
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.dokumentJsonAdapter
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.journalPostFrom
import org.junit.jupiter.api.Test

internal class JournalFøringFerdigstillTest {

    private val journalPostApi = mockk<JournalPostApi>(relaxed = true)
    private val manuellJournalføringsOppgaveClient = mockk<ManuellJournalføringsOppgaveClient>(relaxed = true)
    private val arenaClient = mockk<ArenaClient>(relaxed = true)

    @Test
    fun `Filter is true when packet contains the required values`() {
        erJournalpost.test("", Packet().apply {
            this.putValue(NATURLIG_IDENT, "fnr")
            this.putValue(JOURNALPOST_ID, "journalPostId")
            this.putValue(ARENA_SAK_OPPRETTET, true)
            this.putValue(DOKUMENTER, true)
            this.putValue(AVSENDER_NAVN, true)
            this.putValue(BEHANDLENDE_ENHET, "9999")
        }) shouldBe true
    }

    @Test
    fun `Filter is false when packet does not contain the required values`() {
        erJournalpost.test("", Packet().apply {
            this.putValue(NATURLIG_IDENT, "fnr")
            this.putValue(JOURNALPOST_ID, "journalPostId")
            this.putValue(ARENA_SAK_ID, "arenaSakid")
        }) shouldBe false
    }

    @Test
    fun `Ferdigstill Fagsak når Packet inneholder Arena sak id `() {

        val journalFøringFerdigstill = JournalFøringFerdigstill(journalPostApi, mockk(), arenaClient)
        val journalPostId = "journalPostId"

        val packet = Packet().apply {
            this.putValue(NATURLIG_IDENT, "fnr")
            this.putValue(JOURNALPOST_ID, journalPostId)
            this.putValue(ARENA_SAK_ID, "arenaSakId")
            this.putValue(AVSENDER_NAVN, "et navn")
            this.putValue(DATO_REGISTRERT, "2020-01-01")
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

        val journalFøringFerdigstill = JournalFøringFerdigstill(journalPostApi, manuellJournalføringsOppgaveClient, arenaClient)
        val journalPostId = "journalPostId"
        val aktørId = "12345678910"
        val enhet = "999"

        val packet = Packet().apply {
            this.putValue(NATURLIG_IDENT, "fnr")
            this.putValue(JOURNALPOST_ID, journalPostId)
            this.putValue(AVSENDER_NAVN, "et navn")
            this.putValue(AKTØR_ID, aktørId)
            this.putValue(BEHANDLENDE_ENHET, enhet)
            this.putValue(DATO_REGISTRERT, "2020-01-01")
            dokumentJsonAdapter.toJsonValue(listOf(Dokument("id1", "tittel1")))?.let { this.putValue(DOKUMENTER, it) }
        }

        journalFøringFerdigstill.handlePacket(packet)

        verifyAll {
            manuellJournalføringsOppgaveClient.opprettOppgave(journalPostId, aktørId, "tittel1", enhet)
        }

        verify(exactly = 0) {
            journalPostApi.ferdigstill(any())
        }
    }

    @Test
    fun `Metrikker blir oppdatert når journal poster blir ferdigstilt`() {

        JournalFøringFerdigstill(journalPostApi, manuellJournalføringsOppgaveClient, arenaClient).apply {
            val generellPacket = Packet().apply {
                this.putValue(NATURLIG_IDENT, "fnr")
                this.putValue(AKTØR_ID, "aktør")
                this.putValue(JOURNALPOST_ID, "journalPostId")
                this.putValue(AVSENDER_NAVN, "et navn")
                this.putValue(BEHANDLENDE_ENHET, "9999")
                dokumentJsonAdapter.toJsonValue(listOf(Dokument("id1", "tittel1")))?.let { this.putValue(DOKUMENTER, it) }
            }
            this.handlePacket(generellPacket)

            val fagsakPacket = Packet().apply {
                this.putValue(NATURLIG_IDENT, "fnr")
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




    @Test
    fun `Opprett manuell journalføringsoppgave når bruker er ukjent`() {
        val journalFøringFerdigstill = JournalFøringFerdigstill(journalPostApi, manuellJournalføringsOppgaveClient, arenaClient)
        val journalPostId = "journalPostId"

        val packet = Packet().apply {
            this.putValue(JOURNALPOST_ID, journalPostId)
            this.putValue(BEHANDLENDE_ENHET, "4450")
            dokumentJsonAdapter.toJsonValue(listOf(Dokument("id1", "tittel1")))?.let { this.putValue(DOKUMENTER, it) }
        }

        journalFøringFerdigstill.handlePacket(packet)

        verify { manuellJournalføringsOppgaveClient.opprettOppgave(journalPostId, null, "tittel1", "4450") }
    }

    @Test
    fun `Opprett fagsak og oppgave, og ferdigstill når bruker ikke har aktiv fagsak`() {
        val journalFøringFerdigstill = JournalFøringFerdigstill(journalPostApi, manuellJournalføringsOppgaveClient, arenaClient)
        val journalPostId = "journalPostId"
        val naturligIdent = "12345678910"
        val behandlendeEnhet = "9999"

        every { arenaClient.hentArenaSaker(naturligIdent) } returns emptyList()

        val packet = Packet().apply {
            this.putValue(JOURNALPOST_ID, journalPostId)
            this.putValue(NATURLIG_IDENT, naturligIdent)
            this.putValue(BEHANDLENDE_ENHET, behandlendeEnhet)
            this.putValue(DATO_REGISTRERT, "2020-01-01")
            this.putValue(AKTØR_ID, "987654321")
            this.putValue(AVSENDER_NAVN, "Donald")
            dokumentJsonAdapter.toJsonValue(listOf(Dokument("id1", "tittel1")))?.let { this.putValue(DOKUMENTER, it) }
        }

        journalFøringFerdigstill.handlePacket(packet)

        verify {
            arenaClient.bestillOppgave(naturligIdent, behandlendeEnhet, any())
            journalPostApi.oppdater(journalPostId, any())
            journalPostApi.ferdigstill(journalPostId)
        }
    }

    @Test
    fun `Opprett manuell journalføringsoppgave når bruker har aktiv fagsak`() {
        val journalFøringFerdigstill = JournalFøringFerdigstill(journalPostApi, manuellJournalføringsOppgaveClient, arenaClient)
        val journalPostId = "journalPostId"
        val naturligIdent = "12345678910"
        val behandlendeEnhet = "9999"
        val aktørId = "987654321"

        every { arenaClient.hentArenaSaker(naturligIdent) } returns listOf(ArenaSak(123, ArenaSakStatus.Aktiv))

        val packet = Packet().apply {
            this.putValue(JOURNALPOST_ID, journalPostId)
            this.putValue(NATURLIG_IDENT, naturligIdent)
            this.putValue(BEHANDLENDE_ENHET, behandlendeEnhet)
            this.putValue(DATO_REGISTRERT, "2020-01-01")
            this.putValue(AKTØR_ID, aktørId)
            this.putValue(AVSENDER_NAVN, "Donald")
            dokumentJsonAdapter.toJsonValue(listOf(Dokument("id1", "tittel1")))?.let { this.putValue(DOKUMENTER, it) }
        }

        journalFøringFerdigstill.handlePacket(packet)


        verify { manuellJournalføringsOppgaveClient.opprettOppgave(journalPostId, aktørId, "tittel1", "9999") }
        verify(exactly = 0) { journalPostApi.ferdigstill(any()) }
    }

}
