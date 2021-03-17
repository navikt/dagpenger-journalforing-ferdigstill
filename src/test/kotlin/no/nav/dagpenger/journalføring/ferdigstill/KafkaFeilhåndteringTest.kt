package no.nav.dagpenger.journalføring.ferdigstill

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.finn.unleash.FakeUnleash
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.Dokument
import no.nav.dagpenger.journalføring.ferdigstill.adapter.JournalpostApi
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ManuellJournalføringsOppgaveClient
import no.nav.dagpenger.streams.KafkaAivenCredentials
import no.nav.dagpenger.streams.PacketDeserializer
import no.nav.dagpenger.streams.PacketSerializer
import no.nav.dagpenger.streams.Topic
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.TopologyTestDriver
import org.apache.kafka.streams.test.ConsumerRecordFactory
import org.junit.jupiter.api.Test
import java.util.Properties

class KafkaFeilhåndteringTest {

    private val configuration = Configuration(
        kafka = Configuration.Kafka(
            credential = KafkaAivenCredentials(
                sslTruststorePasswordConfig = "changeme"
            )
        )
    )

    private val streamProperties = Properties().apply {
        this[StreamsConfig.APPLICATION_ID_CONFIG] = "test"
        this[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = "dummy:1234"
    }

    val dagpengerJournalpostTopic: Topic<String, Packet> = Topic(
        "teamdagpenger.journalforing.v1",
        keySerde = Serdes.String(),
        valueSerde = Serdes.serdeFrom(PacketSerializer(), PacketDeserializer())
    )

    val factory = ConsumerRecordFactory<String, Packet>(
        dagpengerJournalpostTopic.name,
        dagpengerJournalpostTopic.keySerde.serializer(),
        dagpengerJournalpostTopic.valueSerde.serializer()
    )

    private val journalpostApi = mockk<JournalpostApi>(relaxed = true)
    private val manuellJournalføringsOppgaveClient = mockk<ManuellJournalføringsOppgaveClient>(relaxed = true)
    private val arenaClient = mockk<ArenaClient>()

    @Test
    fun `skal fortsette der den slapp når noe feiler`() {
        val journalFøringFerdigstill =
            JournalføringFerdigstill(journalpostApi, manuellJournalføringsOppgaveClient, arenaClient, mockk())
        val application = Application(configuration, journalFøringFerdigstill, FakeUnleash())

        val journalPostId = "journalPostId"
        val naturligIdent = "12345678910"
        val behandlendeEnhet = "9999"
        val aktørId = "987654321"

        every { arenaClient.harIkkeAktivSak(any()) } returns true

        every { arenaClient.bestillOppgave(any()) } returns ArenaIdParRespons(
            oppgaveId = OppgaveId("abc"),
            fagsakId = FagsakId("abc")
        )
        every { journalpostApi.oppdater(any(), any()) } throws AdapterException(RuntimeException()) andThen { Unit }

        val packet = Packet().apply {
            this.putValue(PacketKeys.JOURNALPOST_ID, journalPostId)
            this.putValue(PacketKeys.TOGGLE_BEHANDLE_NY_SØKNAD, true)
            this.putValue(PacketKeys.NATURLIG_IDENT, naturligIdent)
            this.putValue(PacketKeys.BEHANDLENDE_ENHET, behandlendeEnhet)
            this.putValue(PacketKeys.DATO_REGISTRERT, "2020-01-01T01:01:01")
            this.putValue(PacketKeys.AKTØR_ID, aktørId)
            this.putValue(PacketKeys.AVSENDER_NAVN, "Donald")
            this.putValue(PacketKeys.HENVENDELSESTYPE, "NY_SØKNAD")
            PacketMapper.dokumentJsonAdapter.toJsonValue(
                listOf(
                    Dokument(
                        "id1",
                        "tittel1"
                    )
                )
            )?.let {
                this.putValue(
                    PacketKeys.DOKUMENTER,
                    it
                )
            }
        }

        TopologyTestDriver(application.buildTopology(), streamProperties).use { topologyTestDriver ->
            val inputRecord = factory.create(packet)
            topologyTestDriver.pipeInput(inputRecord)

            val utMedFagsakId = readOutput(topologyTestDriver)

            utMedFagsakId shouldNotBe null
            utMedFagsakId?.value()?.getStringValue("fagsakId") shouldBe "abc"

            val utFerdigstilt = readOutput(topologyTestDriver)

            utFerdigstilt shouldNotBe null
            utFerdigstilt?.value()?.hasField("ferdigBehandlet")
        }

        verify(exactly = 1) { arenaClient.bestillOppgave(any()) }
    }

    @Test
    fun `skal feile hvis pakken er lest mer enn 10 ganger`() {
        val application = Application(configuration, mockk(), FakeUnleash())

        val journalPostId = "journalPostId"
        val naturligIdent = "12345678910"
        val behandlendeEnhet = "9999"
        val aktørId = "987654321"

        val packet = Packet("{\"system_read_count\": 16}").apply {
            this.putValue(PacketKeys.JOURNALPOST_ID, journalPostId)
            this.putValue(PacketKeys.TOGGLE_BEHANDLE_NY_SØKNAD, true)
            this.putValue(PacketKeys.NATURLIG_IDENT, naturligIdent)
            this.putValue(PacketKeys.BEHANDLENDE_ENHET, behandlendeEnhet)
            this.putValue(PacketKeys.DATO_REGISTRERT, "2020-01-01")
            this.putValue(PacketKeys.AKTØR_ID, aktørId)
            this.putValue(PacketKeys.AVSENDER_NAVN, "Donald")
            this.putValue(PacketKeys.HENVENDELSESTYPE, "NY_SØKNAD")
            PacketMapper.dokumentJsonAdapter.toJsonValue(
                listOf(
                    Dokument(
                        "id1",
                        "tittel1"
                    )
                )
            )?.let {
                this.putValue(
                    PacketKeys.DOKUMENTER,
                    it
                )
            }
        }

        shouldThrow<ReadCountException> {
            application.onPacket(packet)
        }
    }

    private fun readOutput(topologyTestDriver: TopologyTestDriver): ProducerRecord<String, Packet>? {
        return topologyTestDriver.readOutput(
            configuration.kafka.dagpengerJournalpostTopic.name,
            configuration.kafka.dagpengerJournalpostTopic.keySerde.deserializer(),
            configuration.kafka.dagpengerJournalpostTopic.valueSerde.deserializer()
        )
    }
}
