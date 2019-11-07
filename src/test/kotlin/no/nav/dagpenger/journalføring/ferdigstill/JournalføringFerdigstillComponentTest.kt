package no.nav.dagpenger.journalføring.ferdigstill

import com.github.kittinunf.fuel.httpGet
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.kotlintest.shouldBe
import no.nav.common.JAASCredential
import no.nav.common.KafkaEnvironment
import no.nav.common.embeddedutils.getAvailablePort
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.streams.KafkaCredential
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SaslConfigs
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.Properties


class JournalforingFerdigstillComponentTest {


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

        val journalPostApiMock by lazy {
            WireMockServer(wireMockConfig().dynamicPort()).also {
                it.start()
            }
        }

        // given config
        val configuration = Configuration().copy(
            kafka = Configuration.Kafka(
                brokers = embeddedEnvironment.brokersURL,
                credential = KafkaCredential(username, password)),
            application = Configuration.Application(
                httpPort = getAvailablePort()
            ),
            journalPostApiUrl = journalPostApiMock.baseUrl()
        )

        private val app = Application(configuration)
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
            journalPostApiMock.stop()
            app.stop()
        }
    }

    @Test
    fun ` embedded kafka cluster is up and running `() {
        embeddedEnvironment.serverPark.status shouldBe KafkaEnvironment.ServerParkStatus.Started
    }

    @Test
    fun `Mock journal post API  up and running `() {
        "${journalPostApiMock.baseUrl()}/__admin".httpGet().response().second.statusCode shouldBe 200
    }


    @Test
    fun ` Component test of JournalføringFerdigstill`() {

        journalPostApiMock.addStubMapping(
            post(urlEqualTo("/rest/journalpostapi/v1/journalpost/1/ferdigstill"))
                .willReturn(aResponse()
                    .withStatus(200))
                .build()
        )


        behovProducer(configuration).run {
            this.send(ProducerRecord(configuration.kafka.dagpengerJournalpostTopic.name, Packet().apply {
                this.putValue(PacketKeys.ARENA_SAK_ID, "1")
                this.putValue(PacketKeys.JOURNALPOST_ID, "1")
            })).get()
        }

        retry(5) {
            journalPostApiMock.verify(postRequestedFor(urlMatching("/rest/journalpostapi/v1/journalpost/1/ferdigstill")))
        }

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
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${configuration.kafka.credential.username}\" password=\"${configuration.kafka.credential.password}\";"
            )
        })

        return producer
    }

}

fun <T> retry(numOfRetries: Int, sleep: Int = 1000, block: () -> T): T {
    var throwable: Throwable? = null
    (1..numOfRetries).forEach { attempt ->
        try {
            return block()
        } catch (e: Throwable) {
            throwable = e
        }
        Thread.sleep(sleep.toLong())
    }
    throw throwable!!
}
