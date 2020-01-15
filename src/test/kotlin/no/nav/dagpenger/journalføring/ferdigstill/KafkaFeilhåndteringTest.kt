package no.nav.dagpenger.journalføring.ferdigstill

import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaSak
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaSakStatus
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
    private val configuration = Configuration()

    private val streamProperties = Properties().apply {
        this[StreamsConfig.APPLICATION_ID_CONFIG] = "test"
        this[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = "dummy:1234"
    }

    val dagpengerJournalpostTopic: Topic<String, Packet> = Topic(
        "privat-dagpenger-journalpost-mottatt-v1",
        keySerde = Serdes.String(),
        valueSerde = Serdes.serdeFrom(PacketSerializer(), PacketDeserializer())
    )

    val factory = ConsumerRecordFactory<String, Packet>(
        dagpengerJournalpostTopic.name,
        dagpengerJournalpostTopic.keySerde.serializer(),
        dagpengerJournalpostTopic.valueSerde.serializer()
    )

    private val journalpostApi = mockk<JournalPostApi>(relaxed = true)
    private val manuellJournalføringsOppgaveClient = mockk<ManuellJournalføringsOppgaveClient>(relaxed = true)
    private val arenaClient = mockk<ArenaClient>(relaxed = true)

    @Test
    fun `skal fortsette der den slapp når noe feiler`() {
        val journalFøringFerdigstill = JournalFøringFerdigstill(journalpostApi, manuellJournalføringsOppgaveClient, arenaClient)
        val application = Application(configuration, journalFøringFerdigstill)

        val journalPostId = "journalPostId"
        val naturligIdent = "12345678910"
        val behandlendeEnhet = "9999"
        val aktørId = "987654321"

        every { arenaClient.hentArenaSaker(naturligIdent) } returns listOf(ArenaSak(123, ArenaSakStatus.Aktiv))
        every { journalpostApi.oppdater(any(), any()) } throws RuntimeException() andThen { Unit }

        val packet = Packet().apply {
            this.putValue(PacketKeys.JOURNALPOST_ID, journalPostId)
            this.putValue(PacketKeys.NATURLIG_IDENT, naturligIdent)
            this.putValue(PacketKeys.BEHANDLENDE_ENHET, behandlendeEnhet)
            this.putValue(PacketKeys.DATO_REGISTRERT, "2020-01-01")
            this.putValue(PacketKeys.AKTØR_ID, aktørId)
            this.putValue(PacketKeys.AVSENDER_NAVN, "Donald")
            PacketToJoarkPayloadMapper.dokumentJsonAdapter.toJsonValue(listOf(Dokument("id1", "tittel1")))?.let { this.putValue(
                PacketKeys.DOKUMENTER, it) }
        }

        TopologyTestDriver(application.buildTopology(), streamProperties).use { topologyTestDriver ->
            val inputRecord = factory.create(packet)
            topologyTestDriver.pipeInput(inputRecord)

            val ut = readOutput(topologyTestDriver)

            ut shouldNotBe null
            ut?.value()?.getStringValue("arenasakId") shouldBe "abc"
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