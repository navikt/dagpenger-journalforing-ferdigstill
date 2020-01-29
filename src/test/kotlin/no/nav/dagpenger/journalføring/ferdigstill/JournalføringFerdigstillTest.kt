package no.nav.dagpenger.journalføring.ferdigstill

import io.kotlintest.matchers.doubles.shouldBeGreaterThan
import io.kotlintest.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.prometheus.client.CollectorRegistry
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.AKTØR_ID
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.AVSENDER_NAVN
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.BEHANDLENDE_ENHET
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.DATO_REGISTRERT
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.DOKUMENTER
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.HENVENDELSESTYPE
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.JOURNALPOST_ID
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.NATURLIG_IDENT
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.dokumentJsonAdapter
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaSak
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaSakStatus
import no.nav.dagpenger.journalføring.ferdigstill.adapter.BestillOppgaveArenaException
import no.nav.tjeneste.virksomhet.behandlearbeidogaktivitetoppgave.v1.BestillOppgavePersonErInaktiv
import no.nav.tjeneste.virksomhet.behandlearbeidogaktivitetoppgave.v1.BestillOppgavePersonIkkeFunnet
import org.junit.jupiter.api.Test

internal class JournalføringFerdigstillTest {

    private val journalPostApi = mockk<JournalpostApi>(relaxed = true)
    private val manuellJournalføringsOppgaveClient = mockk<ManuellJournalføringsOppgaveClient>(relaxed = true)
    private val arenaClient = mockk<ArenaClient>(relaxed = true)

    @Test
    fun `Skal ta imot pakker med journalpostId`() {

        val application = Application(Configuration(), mockk())

        application.filterPredicates().all { it.test("", Packet().apply {
            this.putValue(JOURNALPOST_ID, "journalPostId")
            this.putValue("toggleBehandleNySøknad", true)
        }) } shouldBe true

        application.filterPredicates().all { it.test("", Packet().apply {
            this.putValue("noe annet", "blabla")
        }) } shouldBe false
    }

    @Test
    fun `Metrikker blir oppdatert når journalposter blir ferdigstilt`() {

        every {
            arenaClient.hentArenaSaker("fnr")
        } returns listOf(ArenaSak(123, ArenaSakStatus.Inaktiv))

        JournalføringFerdigstill(journalPostApi, manuellJournalføringsOppgaveClient, arenaClient).apply {
            val generellPacket = Packet().apply {
                this.putValue(JOURNALPOST_ID, "journalPostId")
                this.putValue(AVSENDER_NAVN, "et navn")
                this.putValue(BEHANDLENDE_ENHET, "9999")
                this.putValue(HENVENDELSESTYPE, "NY_SØKNAD")
                dokumentJsonAdapter.toJsonValue(listOf(Dokument("id1", "tittel1")))?.let { this.putValue(DOKUMENTER, it) }
            }
            this.handlePacket(generellPacket)

            val fagsakPacket = Packet().apply {
                this.putValue(NATURLIG_IDENT, "fnr")
                this.putValue(AKTØR_ID, "aktør")
                this.putValue(JOURNALPOST_ID, "journalPostId")
                this.putValue(AVSENDER_NAVN, "et navn")
                this.putValue(BEHANDLENDE_ENHET, "9999")
                this.putValue(DATO_REGISTRERT, "2020-01-01")
                this.putValue(HENVENDELSESTYPE, "NY_SØKNAD")
                dokumentJsonAdapter.toJsonValue(listOf(Dokument("id1", "tittel1")))?.let { this.putValue(DOKUMENTER, it) }
            }
            this.handlePacket(fagsakPacket)
        }

        CollectorRegistry.defaultRegistry.getSampleValue("dagpenger_journalpost_ferdigstilt", arrayOf("saksType"), arrayOf(SaksType.FAGSAK.name.toLowerCase())) shouldBeGreaterThan 0.0
        CollectorRegistry.defaultRegistry.getSampleValue("dagpenger_journalpost_ferdigstilt", arrayOf("saksType"), arrayOf(SaksType.GENERELL_SAK.name.toLowerCase())) shouldBeGreaterThan 0.0
    }

    @Test
    fun `Opprett manuell journalføringsoppgave når bruker er ukjent`() {
        val journalFøringFerdigstill = JournalføringFerdigstill(journalPostApi, manuellJournalføringsOppgaveClient, arenaClient)
        val journalPostId = "journalPostId"

        val packet = Packet().apply {
            this.putValue(JOURNALPOST_ID, journalPostId)
            this.putValue(BEHANDLENDE_ENHET, "4450")
            this.putValue(HENVENDELSESTYPE, "NY_SØKNAD")
            dokumentJsonAdapter.toJsonValue(listOf(Dokument("id1", "tittel1")))?.let { this.putValue(DOKUMENTER, it) }
        }

        journalFøringFerdigstill.handlePacket(packet)

        verify { manuellJournalføringsOppgaveClient.opprettOppgave(journalPostId, null, "tittel1", "4450") }
        verify(exactly = 0) { journalPostApi.oppdater(any(), any()) }
    }

    @Test
    fun `Opprett fagsak og oppgave, og ferdigstill, når bruker ikke har aktiv fagsak`() {
        val journalFøringFerdigstill = JournalføringFerdigstill(journalPostApi, manuellJournalføringsOppgaveClient, arenaClient)
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
            this.putValue(HENVENDELSESTYPE, "NY_SØKNAD")
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
    fun `Opprett oppgave, og ferdigstill, når brevkode er gjenopptak`() {
        val journalFøringFerdigstill = JournalføringFerdigstill(journalPostApi, manuellJournalføringsOppgaveClient, arenaClient)
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
            this.putValue(HENVENDELSESTYPE, "NY_SØKNAD")
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
        val journalFøringFerdigstill = JournalføringFerdigstill(journalPostApi, manuellJournalføringsOppgaveClient, arenaClient)
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
            this.putValue(HENVENDELSESTYPE, "NY_SØKNAD")
            dokumentJsonAdapter.toJsonValue(listOf(Dokument("id1", "tittel1")))?.let { this.putValue(DOKUMENTER, it) }
        }

        journalFøringFerdigstill.handlePacket(packet)

        verify { manuellJournalføringsOppgaveClient.opprettOppgave(journalPostId, aktørId, "tittel1", "9999") }
        verify(exactly = 0) { journalPostApi.ferdigstill(any()) }
    }

    @Test
    fun `Opprett manuell journalføringsoppgave når bestilling av arena-oppgave feiler`() {
        val journalFøringFerdigstill = JournalføringFerdigstill(journalPostApi, manuellJournalføringsOppgaveClient, arenaClient)
        val journalPostId = "journalPostId"
        val naturligIdent = "12345678910"
        val behandlendeEnhet = "9999"
        val aktørId = "987654321"

        val packetPersonInaktiv = Packet().apply {
            this.putValue(JOURNALPOST_ID, journalPostId)
            this.putValue(NATURLIG_IDENT, naturligIdent)
            this.putValue(BEHANDLENDE_ENHET, behandlendeEnhet)
            this.putValue(DATO_REGISTRERT, "2020-01-01")
            this.putValue(AKTØR_ID, aktørId)
            this.putValue(AVSENDER_NAVN, "Donald")
            this.putValue(HENVENDELSESTYPE, "NY_SØKNAD")
            dokumentJsonAdapter.toJsonValue(listOf(Dokument("id1", "tittel1")))?.let { this.putValue(DOKUMENTER, it) }
        }

        val packetPersonIkkeFunnet = Packet(packetPersonInaktiv.toJson()!!)

        // Person er ikke arbeidssøker
        every {
            arenaClient.bestillOppgave(naturligIdent, behandlendeEnhet, any())
        } throws BestillOppgaveArenaException(BestillOppgavePersonErInaktiv())

        journalFøringFerdigstill.handlePacket(packetPersonInaktiv)

        verify(exactly = 1) { manuellJournalføringsOppgaveClient.opprettOppgave(journalPostId, aktørId, "tittel1", "9999") }
        verify(exactly = 0) { journalPostApi.ferdigstill(any()) }

        // Person er ikke funnet i arena
        every {
            arenaClient.bestillOppgave(naturligIdent, behandlendeEnhet, any())
        } throws BestillOppgaveArenaException(BestillOppgavePersonIkkeFunnet())

        journalFøringFerdigstill.handlePacket(packetPersonIkkeFunnet)

        verify(exactly = 2) { manuellJournalføringsOppgaveClient.opprettOppgave(journalPostId, aktørId, "tittel1", "9999") }
        verify(exactly = 0) { journalPostApi.ferdigstill(any()) }
    }
}
