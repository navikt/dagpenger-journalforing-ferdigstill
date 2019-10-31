package no.nav.dagpenger.journalføring.ferdigstill

import io.kotlintest.matchers.numerics.shouldBeGreaterThan
import io.kotlintest.shouldBe
import mu.KotlinLogging
import no.nav.common.JAASCredential
import no.nav.common.KafkaEnvironment
import no.nav.common.embeddedutils.getAvailablePort
import no.nav.dagpenger.events.Packet
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SaslConfigs
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.Properties

class JournalforingFerdigstillComponentTest {

    private val LOGGER = KotlinLogging.logger {}

    companion object {
        private const val username = "srvkafkaclient"
        private const val password = "kafkaclient"

        val embeddedEnvironment = KafkaEnvironment(
            users = listOf(JAASCredential(username, password)),
            autoStart = false,
            withSchemaRegistry = true,
            withSecurity = true,
            topics = listOf("privat-dagpenger-journalpost-mottatt-v1")
        )

        // given config
        val configuration = Configuration().copy(
            kafka = Configuration.Kafka(brokers = embeddedEnvironment.brokersURL),
            application = Configuration.Application(
                httpPort = getAvailablePort(),
                user = username,
                password = password
            )
        )

        val app = JournalføringFerdigstill(configuration)
        @BeforeAll
        @JvmStatic
        fun setup() {
            embeddedEnvironment.start()
            app.start()
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            embeddedEnvironment.tearDown()
            app.stop()
        }
    }

    @Test
    fun ` embedded kafka cluster is up and running `() {
        embeddedEnvironment.serverPark.status shouldBe KafkaEnvironment.ServerParkStatus.Started
    }

    @Test
    fun ` Component test of JournalføringFerdigstill`() {

        val behovProducer = behovProducer(configuration)

        val record =
            behovProducer.send(ProducerRecord(configuration.kafka.dagpengerJournalpostTopic.name, Packet())).get()
        LOGGER.info { "Produced -> ${record.topic()}  to offset ${record.offset()}" }

        val behovConsumer: KafkaConsumer<String, Packet> = behovConsumer(configuration)
        val journalføringer = behovConsumer.poll(Duration.ofSeconds(5)).toList()
        journalføringer.size shouldBeGreaterThan 0
    }

    private fun behovProducer(configuration: Configuration): KafkaProducer<String, Packet> {
        val topic = configuration.kafka.dagpengerJournalpostTopic
        val producer: KafkaProducer<String, Packet> = KafkaProducer(Properties().apply {
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, configuration.kafka.brokers)
            put(ProducerConfig.CLIENT_ID_CONFIG, "dummy-behov-producer")
            put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                topic.keySerde.serializer().javaClass.name
            )
            put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                topic.valueSerde.serializer().javaClass.name
            )
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT")
            put(SaslConfigs.SASL_MECHANISM, "PLAIN")
            put(
                SaslConfigs.SASL_JAAS_CONFIG,
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${configuration.application.user}\" password=\"${configuration.application.password}\";"
            )
        })

        return producer
    }

    private fun behovConsumer(configuration: Configuration): KafkaConsumer<String, Packet> {
        val topic = configuration.kafka.dagpengerJournalpostTopic
        val consumer: KafkaConsumer<String, Packet> = KafkaConsumer(Properties().apply {

            put(ConsumerConfig.GROUP_ID_CONFIG, "test-dagpenger-ferdigstill-consumer")
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, configuration.kafka.brokers)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                topic.keySerde.deserializer().javaClass.name
            )
            put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                topic.valueSerde.deserializer().javaClass.name
            )
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT")
            put(SaslConfigs.SASL_MECHANISM, "PLAIN")
            put(
                SaslConfigs.SASL_JAAS_CONFIG,
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${configuration.application.user}\" password=\"${configuration.application.password}\";"
            )
        })

        consumer.subscribe(listOf(topic.name))
        return consumer
    }
}
