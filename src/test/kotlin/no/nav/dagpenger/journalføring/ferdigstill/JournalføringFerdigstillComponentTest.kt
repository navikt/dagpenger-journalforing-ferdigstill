package no.nav.dagpenger.journalføring.ferdigstill

import com.github.kittinunf.fuel.httpGet
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.patch
import com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.matching.EqualToJsonPattern
import io.kotlintest.shouldBe
import no.finn.unleash.FakeUnleash
import no.nav.common.JAASCredential
import no.nav.common.KafkaEnvironment
import no.nav.common.embeddedutils.getAvailablePort
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.JournalPostRestApi.Companion.toJsonPayload
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.dokumentJsonAdapter
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.journalPostFrom
import no.nav.dagpenger.oidc.StsOidcClient
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
import org.junit.jupiter.api.fail
import java.util.Properties

internal class JournalforingFerdigstillComponentTest {

    companion object {
        private const val username = "srvkafkaclient"
        private const val password = "kafkaclient"

        val embeddedEnvironment = KafkaEnvironment(
            users = listOf(JAASCredential(username, password)),
            autoStart = false,
            withSchemaRegistry = true,
            withSecurity = true,
            topicInfos = listOf(KafkaEnvironment.TopicInfo("privat-dagpenger-journalpost-mottatt-v1"))
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
            sts = Configuration.Sts(
                baseUrl = journalPostApiMock.baseUrl()
            ),
            journalPostApiUrl = journalPostApiMock.baseUrl()
        )

        val stsOidcClient = StsOidcClient(configuration.sts.baseUrl, configuration.sts.username, configuration.sts.password)
        val journalFøringFerdigstill = JournalFøringFerdigstill(JournalPostRestApi(configuration.journalPostApiUrl, stsOidcClient))
        val unleash = FakeUnleash().apply {
            this.enableAll()
        }

        private val app = Application(configuration, journalFøringFerdigstill, unleash)
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
            get(urlEqualTo("/rest/v1/sts/token/?grant_type=client_credentials&scope=openid"))
                .willReturn(okJson("""
                   {
                     "access_token": "token",
                     "token_type": "Bearer",
                     "expires_in": 3600
                    } 
                """.trimIndent()
                ))
                .build()
        )

        val journalPostId = "journalPostId"
        journalPostApiMock.addStubMapping(
            patch(urlEqualTo("/rest/journalpostapi/v1/journalpost/$journalPostId/ferdigstill"))
                .willReturn(aResponse()
                    .withStatus(200))
                .build()
        )

        journalPostApiMock.addStubMapping(
            put(urlEqualTo("/rest/journalpostapi/v1/journalpost/$journalPostId"))
                .willReturn(aResponse()
                    .withStatus(200))
                .build()
        )

        val expectedFerdigstillJson = """{ "journalfoerendeEnhet" : "9999"}"""

        val packet = Packet().apply {
            this.putValue(PacketKeys.ARENA_SAK_OPPRETTET, true)
            this.putValue(PacketKeys.FNR, "fnr")
            this.putValue(PacketKeys.JOURNALPOST_ID, journalPostId)
            this.putValue(PacketKeys.ARENA_SAK_ID, "arenaSakId")
            this.putValue(PacketKeys.AVSENDER_NAVN, "et navn")
            dokumentJsonAdapter.toJsonValue(listOf(Dokument("id1", "tittel1"), Dokument("id1", "tittel1")))?.let { this.putValue(PacketKeys.DOKUMENTER, it) }
        }

        val json = journalPostFrom(packet).let { toJsonPayload(it) }

        behovProducer(configuration).run {
            this.send(ProducerRecord(configuration.kafka.dagpengerJournalpostTopic.name, packet)).get()
        }

        retry {
            journalPostApiMock.verify(1,
                putRequestedFor(urlMatching("/rest/journalpostapi/v1/journalpost/$journalPostId"))
                    .withRequestBody(EqualToJsonPattern(json, true, false))
                    .withHeader("Content-Type", equalTo("application/json")).withHeader("Authorization", equalTo("Bearer token")))

            journalPostApiMock.verify(1, patchRequestedFor(urlMatching("/rest/journalpostapi/v1/journalpost/$journalPostId/ferdigstill"))
                .withRequestBody(EqualToJsonPattern(expectedFerdigstillJson, true, false))
                .withHeader("Content-Type", equalTo("application/json")).withHeader("Authorization", equalTo("Bearer token")))
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

private fun <T> retry(numOfRetries: Int = 5, sleep: Long = 1000, block: () -> T): T {
    var throwable: Throwable? = null
    (1..numOfRetries).forEach { _ ->
        try {
            return block()
        } catch (e: Throwable) {
            throwable = e
        }
        Thread.sleep(sleep)
    }
    fail("Failed after retry", throwable)
}
