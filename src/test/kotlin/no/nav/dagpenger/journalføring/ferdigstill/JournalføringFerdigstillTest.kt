package no.nav.dagpenger.journalføring.ferdigstill

import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.prometheus.client.CollectorRegistry
import no.finn.unleash.FakeUnleash
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.AKTØR_ID
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.AVSENDER_NAVN
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.BEHANDLENDE_ENHET
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.DATO_REGISTRERT
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.DOKUMENTER
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.HENVENDELSESTYPE
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.JOURNALPOST_ID
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.NATURLIG_IDENT
import no.nav.dagpenger.journalføring.ferdigstill.PacketMapper.dokumentJsonAdapter
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.Dokument
import no.nav.dagpenger.journalføring.ferdigstill.adapter.JournalpostApi
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ManuellJournalføringsOppgaveClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.OppgaveCommand
import no.nav.dagpenger.journalføring.ferdigstill.adapter.Oppgavetype
import no.nav.dagpenger.journalføring.ferdigstill.adapter.StartVedtakCommand
import no.nav.dagpenger.journalføring.ferdigstill.adapter.VurderFornyetRettighetCommand
import no.nav.dagpenger.journalføring.ferdigstill.adapter.VurderHenvendelseAngåendeEksisterendeSaksforholdCommand
import no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester.MinsteArbeidsinntektVilkår
import no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester.Vilkårtester
import no.nav.dagpenger.streams.KafkaAivenCredentials
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId

internal class JournalføringFerdigstillTest {

    private val journalPostApi = mockk<JournalpostApi>(relaxed = true)
    private val manuellJournalføringsOppgaveClient = mockk<ManuellJournalføringsOppgaveClient>(relaxed = true)
    private val arenaClient = mockk<ArenaClient>(relaxed = true)
    private val config = Configuration(
        kafka = Configuration.Kafka(
            credential = KafkaAivenCredentials(
                sslTruststorePasswordConfig = "changeme"
            )
        )
    )

    @Test
    fun `Skal ta imot pakker med journalpostId`() {
        val application = Application(config, mockk(), FakeUnleash())

        application.filterPredicates().all {
            it.test(
                "",
                Packet().apply {
                    this.putValue(JOURNALPOST_ID, "journalPostId")
                    this.putValue("toggleBehandleNySøknad", true)
                }
            )
        } shouldBe true

        application.filterPredicates().all {
            it.test(
                "",
                Packet().apply {
                    this.putValue("noe annet", "blabla")
                }
            )
        } shouldBe false
    }

    @Test
    fun `skal ikke behandle pakker som er ferdig behandlet`() {
        val application = Application(config, mockk(), FakeUnleash())

        application.filterPredicates().all {
            it.test(
                "",
                Packet().apply {
                    this.putValue(JOURNALPOST_ID, "journalPostId")
                    this.putValue(PacketKeys.FERDIG_BEHANDLET, true)
                }
            )
        } shouldBe false
    }

    @Test
    fun `Metrikker blir oppdatert når journalposter blir ferdigstilt`() {

        every {
            arenaClient.harIkkeAktivSak(any())
        } returns true

        JournalføringFerdigstill(
            journalPostApi,
            manuellJournalføringsOppgaveClient,
            arenaClient,
            mockk()
        ).apply {
            val generellPacket = Packet().apply {
                this.putValue(JOURNALPOST_ID, "journalPostId")
                this.putValue(AVSENDER_NAVN, "et navn")
                this.putValue(BEHANDLENDE_ENHET, "9999")
                this.putValue(HENVENDELSESTYPE, "NY_SØKNAD")
                this.putValue(DATO_REGISTRERT, "2020-01-01T01:01:01")

                dokumentJsonAdapter.toJsonValue(
                    listOf(
                        Dokument(
                            "id1",
                            "tittel1"
                        )
                    )
                )?.let { this.putValue(DOKUMENTER, it) }
            }
            this.handlePacket(generellPacket)

            val fagsakPacket = Packet().apply {
                this.putValue(NATURLIG_IDENT, "fnr")
                this.putValue(AKTØR_ID, "aktør")
                this.putValue(JOURNALPOST_ID, "journalPostId")
                this.putValue(AVSENDER_NAVN, "et navn")
                this.putValue(BEHANDLENDE_ENHET, "9999")
                this.putValue(DATO_REGISTRERT, "2020-01-01T01:01:01")
                this.putValue(HENVENDELSESTYPE, "NY_SØKNAD")
                dokumentJsonAdapter.toJsonValue(
                    listOf(
                        Dokument(
                            "id1",
                            "tittel1"
                        )
                    )
                )?.let { this.putValue(DOKUMENTER, it) }
            }
            this.handlePacket(fagsakPacket)
        }

        CollectorRegistry.defaultRegistry.getSampleValue("dagpenger_journalpost_ferdigstilt") shouldBeGreaterThan 0.0
        CollectorRegistry.defaultRegistry.getSampleValue(
            "time_spent_in_chain_count",
            listOf("chain_name").toTypedArray(),
            listOf("OppdaterJournalpostBehandlingsChain").toTypedArray()
        ) shouldBeGreaterThan 0.0
    }

    @Test
    fun `Opprett manuell journalføringsoppgave vi ikke har fødselsnummer`() {
        val journalFøringFerdigstill =
            JournalføringFerdigstill(
                journalPostApi,
                manuellJournalføringsOppgaveClient,
                arenaClient,
                mockk()
            )
        val journalPostId = "journalPostId"
        val dato = "2020-01-01T01:01:01"
        val zonedDateTime = LocalDateTime.parse(dato).atZone(ZoneId.of("Europe/Oslo"))

        val packet = Packet().apply {
            this.putValue(JOURNALPOST_ID, journalPostId)
            this.putValue(BEHANDLENDE_ENHET, "4450")
            this.putValue(HENVENDELSESTYPE, "NY_SØKNAD")
            this.putValue(DATO_REGISTRERT, dato)
            dokumentJsonAdapter.toJsonValue(
                listOf(
                    Dokument(
                        "id1",
                        "tittel1"
                    )
                )
            )?.let { this.putValue(DOKUMENTER, it) }
        }

        journalFøringFerdigstill.handlePacket(packet)

        verify {
            manuellJournalføringsOppgaveClient.opprettOppgave(
                journalPostId,
                null,
                "tittel1",
                "4450",
                zonedDateTime
            )
        }
        verify(exactly = 0) { journalPostApi.oppdater(any(), any()) }
    }

    @Test
    fun `Opprett fagsak og oppgave, og ferdigstill, når bruker ikke har aktiv fagsak`() {
        val journalFøringFerdigstill =
            JournalføringFerdigstill(
                journalPostApi,
                manuellJournalføringsOppgaveClient,
                arenaClient,
                mockk()
            )
        val journalPostId = "journalPostId"
        val naturligIdent = "12345678910"
        val behandlendeEnhet = "9999"

        val slot = slot<OppgaveCommand>()

        every { arenaClient.harIkkeAktivSak(any()) } returns true
        every { arenaClient.bestillOppgave(command = capture(slot)) } returns
            ArenaIdParRespons(
                OppgaveId("abc"),
                FagsakId("123")
            )

        val packet = Packet().apply {
            this.putValue(JOURNALPOST_ID, journalPostId)
            this.putValue(NATURLIG_IDENT, naturligIdent)
            this.putValue(BEHANDLENDE_ENHET, behandlendeEnhet)
            this.putValue(AKTØR_ID, "987654321")
            this.putValue(DATO_REGISTRERT, "2020-01-01T01:01:01")
            this.putValue(AVSENDER_NAVN, "Donald")
            this.putValue(HENVENDELSESTYPE, "NY_SØKNAD")
            dokumentJsonAdapter.toJsonValue(
                listOf(
                    Dokument(
                        "id1",
                        "tittel1"
                    )
                )
            )?.let { this.putValue(DOKUMENTER, it) }
        }

        journalFøringFerdigstill.handlePacket(packet)

        verify {
            arenaClient.bestillOppgave(any())
            journalPostApi.oppdater(journalPostId, any())
            journalPostApi.ferdigstill(journalPostId)
        }

        slot.captured.shouldBeTypeOf<StartVedtakCommand>()
        slot.captured.behandlendeEnhetId shouldBe behandlendeEnhet
        slot.captured.naturligIdent shouldBe naturligIdent
    }

    @Test
    fun `Opprett oppgave, og ferdigstill, når brevkode er gjenopptak`() {
        testHenvendelseAngåendeEksisterendeSaksforhold("GJENOPPTAK")
    }

    @Test
    fun `Opprett oppgave, og ferdigstill, når henvendelsestype er utdanning`() {
        testHenvendelseAngåendeEksisterendeSaksforhold("UTDANNING")
    }

    @Test
    fun `Opprett oppgave, og ferdigstill, når henvendelsestype er etablering`() {
        testHenvendelseAngåendeEksisterendeSaksforhold("ETABLERING")
    }

    @Test
    fun `Opprett oppgave, og ferdigstill, når henvendelsestype er klage_anke`() {
        testHenvendelseAngåendeEksisterendeSaksforhold("KLAGE_ANKE")
    }

    @Test
    fun `Opprett oppgave, og ferdigstill, når henvendelsestype er ettersendelse`() {
        testHenvendelseAngåendeEksisterendeSaksforhold("ETTERSENDELSE")
    }

    @Test
    fun `Ved kandidat for avslag basert på minsteinntekt uten permittering havner på egen kø`() {
        val vilkårtester = mockk<Vilkårtester>()
        val journalFøringFerdigstill =
            JournalføringFerdigstill(
                journalPostApi,
                manuellJournalføringsOppgaveClient,
                arenaClient,
                vilkårtester
            )
        val journalPostId = "journalPostId"
        val naturligIdent = "12345678910"
        val behandlendeEnhet = "4450"

        val slot = slot<OppgaveCommand>()

        every { vilkårtester.hentMinsteArbeidsinntektVilkår(any()) } returns MinsteArbeidsinntektVilkår(false, false)
        every { arenaClient.bestillOppgave(command = capture(slot)) } returns
            ArenaIdParRespons(
                oppgaveId = OppgaveId(
                    "abc"
                ),
                fagsakId = FagsakId("123")
            )

        every { arenaClient.harIkkeAktivSak(any()) } returns true

        val packet = lagPacket(journalPostId, naturligIdent, behandlendeEnhet, "NY_SØKNAD")

        journalFøringFerdigstill.handlePacket(packet)

        verify {
            arenaClient.bestillOppgave(any())
            journalPostApi.oppdater(journalPostId, any())
            journalPostApi.ferdigstill(journalPostId)
        }

        slot.captured.shouldBeTypeOf<StartVedtakCommand>()
        slot.captured.oppgavebeskrivelse shouldBe "Minsteinntekt - mulig avslag\n"
        slot.captured.behandlendeEnhetId shouldBe ENHET_FOR_HURTIG_AVSLAG_IKKE_PERMITTERT
    }

    @Test
    fun `Ved kandidat for avslag basert på minsteinntekt med permitterting havner på egen kø`() {
        val vilkårtester = mockk<Vilkårtester>()
        val journalFøringFerdigstill =
            JournalføringFerdigstill(
                journalPostApi,
                manuellJournalføringsOppgaveClient,
                arenaClient,
                vilkårtester
            )
        val journalPostId = "journalPostId"
        val naturligIdent = "12345678910"
        val behandlendeEnhet = "4455"

        val slot = slot<OppgaveCommand>()

        every { vilkårtester.hentMinsteArbeidsinntektVilkår(any()) } returns MinsteArbeidsinntektVilkår(false, false)
        every { arenaClient.bestillOppgave(command = capture(slot)) } returns
            ArenaIdParRespons(
                oppgaveId = OppgaveId(
                    "abc"
                ),
                fagsakId = FagsakId("123")
            )

        every { arenaClient.harIkkeAktivSak(any()) } returns true

        val packet = lagPacket(journalPostId, naturligIdent, behandlendeEnhet, "NY_SØKNAD")

        journalFøringFerdigstill.handlePacket(packet)

        verify {
            arenaClient.bestillOppgave(any())
            journalPostApi.oppdater(journalPostId, any())
            journalPostApi.ferdigstill(journalPostId)
        }

        slot.captured.shouldBeTypeOf<StartVedtakCommand>()
        slot.captured.oppgavebeskrivelse shouldBe "Minsteinntekt - mulig avslag\n"
        slot.captured.behandlendeEnhetId shouldBe ENHET_FOR_HURTIG_AVSLAG_PERMITTERT
    }

    @Test
    fun `legg oppgaveId på pakken`() {
        val actualOppgaveId = "abc"
        val journalFøringFerdigstill =
            JournalføringFerdigstill(
                journalPostApi,
                manuellJournalføringsOppgaveClient,
                arenaClient,
                mockk()
            )
        val journalPostId = "journalPostId"
        val naturligIdent = "12345678910"
        val behandlendeEnhet = "9999"

        val slot = slot<OppgaveCommand>()

        every { arenaClient.bestillOppgave(command = capture(slot)) } returns
            ArenaIdParRespons(
                oppgaveId = OppgaveId(
                    actualOppgaveId
                )
            )

        every { arenaClient.harIkkeAktivSak(any()) } returns true

        val packet = lagPacket(journalPostId, naturligIdent, behandlendeEnhet, "NY_SØKNAD")

        val finishedPacket = journalFøringFerdigstill.handlePacket(packet)

        finishedPacket.getStringValue("oppgaveId") shouldBe actualOppgaveId
    }

    @Test
    private fun testHenvendelseAngåendeEksisterendeSaksforhold(henvendelsestype: String) {
        val journalFøringFerdigstill =
            JournalføringFerdigstill(
                journalPostApi,
                manuellJournalføringsOppgaveClient,
                arenaClient,
                mockk()
            )
        val journalPostId = "journalPostId"
        val naturligIdent = "12345678910"
        val behandlendeEnhet = "9999"

        val slot = slot<OppgaveCommand>()

        every { arenaClient.bestillOppgave(command = capture(slot)) } returns
            ArenaIdParRespons(
                oppgaveId = OppgaveId(
                    "abc"
                )
            )

        every { arenaClient.harIkkeAktivSak(any()) } returns true

        val packet = lagPacket(journalPostId, naturligIdent, behandlendeEnhet, henvendelsestype)

        val finishedPacket = journalFøringFerdigstill.handlePacket(packet)

        verify {
            arenaClient.bestillOppgave(any())
            journalPostApi.oppdater(journalPostId, any())
            journalPostApi.ferdigstill(journalPostId)
        }

        slot.captured.shouldBeTypeOf<VurderHenvendelseAngåendeEksisterendeSaksforholdCommand>()
        slot.captured.behandlendeEnhetId shouldBe behandlendeEnhet
        slot.captured.naturligIdent shouldBe naturligIdent

        finishedPacket.getBoolean("ferdigBehandlet") shouldBe true
    }

    @Test
    fun testManuell() {
        val journalFøringFerdigstill =
            JournalføringFerdigstill(
                journalPostApi,
                manuellJournalføringsOppgaveClient,
                arenaClient,
                mockk()
            )
        val journalPostId = "journalPostId"
        val naturligIdent = "12345678910"
        val behandlendeEnhet = "4450"

        val packet = lagPacket(journalPostId, naturligIdent, behandlendeEnhet, "MANUELL")

        val finishedPacket = journalFøringFerdigstill.handlePacket(packet)

        verify(exactly = 0) {
            arenaClient.bestillOppgave(any())
            journalPostApi.ferdigstill(journalPostId)
        }
        verify {
            manuellJournalføringsOppgaveClient.opprettOppgave(journalPostId, any(), any(), behandlendeEnhet, any())
        }

        finishedPacket.getBoolean("ferdigBehandlet") shouldBe true
    }

    @Test
    fun ` test lønnskompenensasjon klage og anke`() {
        val journalFøringFerdigstill =
            JournalføringFerdigstill(
                journalPostApi,
                manuellJournalføringsOppgaveClient,
                arenaClient,
                mockk()
            )
        val journalPostId = "journalPostId"
        val naturligIdent = "12345678910"
        val behandlendeEnhet = "4486"

        val packet = lagPacket(journalPostId, naturligIdent, behandlendeEnhet, "KLAGE_ANKE_LONNSKOMPENSASJON")

        val finishedPacket = journalFøringFerdigstill.handlePacket(packet)

        verify(exactly = 0) {
            arenaClient.bestillOppgave(any())
            journalPostApi.ferdigstill(journalPostId)
        }
        verify {
            manuellJournalføringsOppgaveClient.opprettOppgave(journalPostId, any(), any(), behandlendeEnhet, any(), oppgavetype = Oppgavetype.VurderHenvendelse)
        }

        finishedPacket.getBoolean("ferdigBehandlet") shouldBe true
    }

    private fun lagPacket(
        journalPostId: String,
        naturligIdent: String,
        behandlendeEnhet: String,
        henvendelsestype: String = "NY_SØKNAD",
    ) =
        Packet().apply {
            this.putValue(JOURNALPOST_ID, journalPostId)
            this.putValue(NATURLIG_IDENT, naturligIdent)
            this.putValue(BEHANDLENDE_ENHET, behandlendeEnhet)
            this.putValue(DATO_REGISTRERT, "2020-01-01T01:01:01")
            this.putValue(AKTØR_ID, "987654321")
            this.putValue(AVSENDER_NAVN, "Donald")
            this.putValue(HENVENDELSESTYPE, henvendelsestype)
            dokumentJsonAdapter.toJsonValue(
                listOf(
                    Dokument(
                        "id1",
                        "tittel1"
                    )
                )
            )?.let { this.putValue(DOKUMENTER, it) }
        }

    @Test
    fun `Opprett manuell journalføringsoppgave når bruker har aktiv fagsak`() {
        val journalFøringFerdigstill =
            JournalføringFerdigstill(
                journalPostApi,
                manuellJournalføringsOppgaveClient,
                arenaClient,
                mockk()
            )
        val journalPostId = "journalPostId"
        val naturligIdent = "12345678910"
        val behandlendeEnhet = "9999"
        val aktørId = "987654321"

        every { arenaClient.harIkkeAktivSak(any()) } returns false

        val packet = Packet().apply {
            this.putValue(JOURNALPOST_ID, journalPostId)
            this.putValue(NATURLIG_IDENT, naturligIdent)
            this.putValue(BEHANDLENDE_ENHET, behandlendeEnhet)
            this.putValue(AKTØR_ID, aktørId)
            this.putValue(DATO_REGISTRERT, "2020-01-01T01:01:01")
            this.putValue(AVSENDER_NAVN, "Donald")
            this.putValue(HENVENDELSESTYPE, "NY_SØKNAD")
            dokumentJsonAdapter.toJsonValue(
                listOf(
                    Dokument(
                        "id1",
                        "tittel1"
                    )
                )
            )?.let { this.putValue(DOKUMENTER, it) }
        }

        journalFøringFerdigstill.handlePacket(packet)

        verify { manuellJournalføringsOppgaveClient.opprettOppgave(journalPostId, aktørId, "tittel1", "9999", any()) }
        verify(exactly = 0) { journalPostApi.ferdigstill(any()) }
    }

    @Test
    fun `Opprett manuell journalføringsoppgave når bestilling av arena-oppgave feiler`() {
        val journalFøringFerdigstill =
            JournalføringFerdigstill(
                journalPostApi,
                manuellJournalføringsOppgaveClient,
                arenaClient,
                mockk()
            )
        val journalPostId = "journalPostId"
        val naturligIdent = "12345678910"
        val behandlendeEnhet = "9999"
        val aktørId = "987654321"
        val dato = "2020-01-01T01:01:01"
        val zonedDateTime = LocalDateTime.parse(dato).atZone(ZoneId.of("Europe/Oslo"))

        val packetPersonInaktiv = Packet().apply {
            this.putValue(JOURNALPOST_ID, journalPostId)
            this.putValue(NATURLIG_IDENT, naturligIdent)
            this.putValue(BEHANDLENDE_ENHET, behandlendeEnhet)
            this.putValue(AKTØR_ID, aktørId)
            this.putValue(DATO_REGISTRERT, dato)
            this.putValue(AVSENDER_NAVN, "Donald")
            this.putValue(HENVENDELSESTYPE, "NY_SØKNAD")

            dokumentJsonAdapter.toJsonValue(
                listOf(
                    Dokument(
                        "id1",
                        "tittel1"
                    )
                )
            )?.let { this.putValue(DOKUMENTER, it) }
        }

        val packetPersonIkkeFunnet = Packet(packetPersonInaktiv.toJson()!!)

        // Person er ikke arbeidssøker
        every {
            arenaClient.bestillOppgave(any())
        } returns null

        every {
            arenaClient.harIkkeAktivSak(any())
        } returns true

        journalFøringFerdigstill.handlePacket(packetPersonInaktiv)

        verify(exactly = 1) {
            manuellJournalføringsOppgaveClient.opprettOppgave(
                journalPostId,
                aktørId,
                "tittel1",
                "9999",
                zonedDateTime
            )
        }
        verify(exactly = 0) { journalPostApi.ferdigstill(any()) }

        // Person er ikke funnet i arena
        every {
            arenaClient.bestillOppgave(any())
        } returns null

        journalFøringFerdigstill.handlePacket(packetPersonIkkeFunnet)

        verify(exactly = 2) {
            manuellJournalføringsOppgaveClient.opprettOppgave(
                journalPostId,
                aktørId,
                "tittel1",
                "9999",
                zonedDateTime
            )
        }
        verify(exactly = 0) { journalPostApi.ferdigstill(any()) }
    }

    @Test
    fun `Opprett manuell journalføringsoppgave når bestilling av arena-oppgave feiler for henvendelser angående eksisterende saksforhold`() {
        val journalFøringFerdigstill =
            JournalføringFerdigstill(
                journalPostApi,
                manuellJournalføringsOppgaveClient,
                arenaClient,
                mockk()
            )
        val journalPostId = "journalPostId"
        val naturligIdent = "12345678910"
        val behandlendeEnhet = "9999"
        val aktørId = "987654321"
        val dato = "2020-01-01T01:01:01"
        val zonedDateTime = LocalDateTime.parse(dato).atZone(ZoneId.of("Europe/Oslo"))

        val packetPersonInaktiv = Packet().apply {
            this.putValue(JOURNALPOST_ID, journalPostId)
            this.putValue(NATURLIG_IDENT, naturligIdent)
            this.putValue(BEHANDLENDE_ENHET, behandlendeEnhet)
            this.putValue(AKTØR_ID, aktørId)
            this.putValue(DATO_REGISTRERT, dato)
            this.putValue(AVSENDER_NAVN, "Donald")
            this.putValue(HENVENDELSESTYPE, "ETABLERING")

            dokumentJsonAdapter.toJsonValue(
                listOf(
                    Dokument(
                        "id1",
                        "tittel1"
                    )
                )
            )?.let { this.putValue(DOKUMENTER, it) }
        }

        // Person er ikke arbeidssøker
        every {
            arenaClient.bestillOppgave(any())
        } returns null

        every {
            arenaClient.harIkkeAktivSak(any())
        } returns true

        journalFøringFerdigstill.handlePacket(packetPersonInaktiv)

        verify(exactly = 1) {
            manuellJournalføringsOppgaveClient.opprettOppgave(
                journalPostId,
                aktørId,
                "tittel1",
                "9999",
                zonedDateTime
            )
        }
        verify(exactly = 0) { journalPostApi.ferdigstill(any()) }
    }

    @Test
    fun `Opprett behandle henvendelse mot Arena når søknaden er søknad om fornyet rettighet - `() {

        val journalFøringFerdigstill =
            JournalføringFerdigstill(
                journalPostApi,
                manuellJournalføringsOppgaveClient,
                arenaClient,
                mockk()
            )
        val journalPostId = "journalPostId"
        val naturligIdent = "12345678910"
        val behandlendeEnhet = "9999"

        val slot = slot<OppgaveCommand>()

        every { arenaClient.bestillOppgave(command = capture(slot)) } returns
            ArenaIdParRespons(
                oppgaveId = OppgaveId(
                    "abc"
                )
            )

        val packet = lagPacket(journalPostId, naturligIdent, behandlendeEnhet, "NY_SØKNAD")
        packet.putValue("søknadsdata", "soknadsdata.json".getJsonResource())

        val behandletPakke = journalFøringFerdigstill.handlePacket(packet)

        verify(exactly = 1) {
            arenaClient.bestillOppgave(any())
            journalPostApi.oppdater(journalPostId, any())
            journalPostApi.ferdigstill(journalPostId)
        }

        slot.isCaptured shouldBe true
        slot.captured.shouldBeTypeOf<VurderFornyetRettighetCommand>()
        slot.captured.behandlendeEnhetId shouldBe "4451"
        slot.captured.naturligIdent shouldBe naturligIdent

        behandletPakke.getBoolean("ferdigBehandlet") shouldBe true
    }
}
