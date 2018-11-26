package no.nav.dagpenger.journalføring.ferdigstill

import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig
import mu.KotlinLogging
import no.nav.common.JAASCredential
import no.nav.common.KafkaEnvironment
import no.nav.common.embeddedutils.getAvailablePort
import no.nav.dagpenger.events.avro.Annet
import no.nav.dagpenger.events.avro.Behov
import no.nav.dagpenger.events.avro.Dokument
import no.nav.dagpenger.events.avro.Ettersending
import no.nav.dagpenger.events.avro.HenvendelsesType
import no.nav.dagpenger.events.avro.Journalpost
import no.nav.dagpenger.events.avro.Mottaker
import no.nav.dagpenger.events.avro.Søknad
import no.nav.dagpenger.streams.Topics
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SaslConfigs
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.util.Properties
import java.util.Random
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JournalføringFerdigstillComponentTest {

    private val LOGGER = KotlinLogging.logger {}

    companion object {
        private const val username = "srvkafkaclient"
        private const val password = "kafkaclient"

        val embeddedEnvironment = KafkaEnvironment(
            users = listOf(JAASCredential(username, password)),
            autoStart = false,
            withSchemaRegistry = true,
            withSecurity = true,
            topics = listOf(Topics.JOARK_EVENTS.name, Topics.INNGÅENDE_JOURNALPOST.name)
        )

        @BeforeClass
        @JvmStatic
        fun setup() {
            embeddedEnvironment.start()
        }

        @AfterClass
        @JvmStatic
        fun teardown() {
            embeddedEnvironment.tearDown()
        }
    }

    @Test
    fun ` embedded kafka cluster is up and running `() {
        assertEquals(embeddedEnvironment.serverPark.status, KafkaEnvironment.ServerParkStatus.Started)
    }

    @Test
    fun ` Should ferdigstille journalposter that has fagsakid and henvendelsestype 'søknad' or 'ettersending' `() {

        val env = Environment(
            dagpengerOppslagUrl = "localhost",
            username = username,
            password = password,
            bootstrapServersUrl = embeddedEnvironment.brokersURL,
            schemaRegistryUrl = embeddedEnvironment.schemaRegistry!!.url,
            httpPort = getAvailablePort()
        )

        val henvendelsesType = mapOf(
            Random().nextLong().toString() to Søknad(),
            Random().nextLong().toString() to Ettersending(),
            Random().nextLong().toString() to Annet(),
            Random().nextLong().toString() to Søknad(),
            Random().nextLong().toString() to Søknad(),
            Random().nextLong().toString() to Annet(),
            Random().nextLong().toString() to Ettersending(),
            Random().nextLong().toString() to Ettersending(),
            Random().nextLong().toString() to Annet(),
            Random().nextLong().toString() to Annet()
        )

        val oppslagClient = DummyOppslagsClient(henvendelsesType.keys)

        val journalføringFerdigstill = JournalføringFerdigstill(env, oppslagClient)

        //produce behov...

        val behovProducer = behovProducer(env)

        journalføringFerdigstill.start()


        henvendelsesType.forEach { journalpostId, henvendelse ->
            val innkommendeBehov: Behov = Behov
                .newBuilder()
                .setBehovId(UUID.randomUUID().toString())
                .setFagsakId(UUID.randomUUID().toString())
                .setMottaker(Mottaker(UUID.randomUUID().toString()))
                .setHenvendelsesType(
                    HenvendelsesType.newBuilder().apply {
                        when (henvendelse) {
                            Søknad() -> søknad = henvendelse as Søknad?
                            Ettersending() -> ettersending = henvendelse as Ettersending?
                            Annet() -> annet = henvendelse as Annet?
                        }
                    }.build()
                )
                .setJournalpost(
                    Journalpost
                        .newBuilder()
                        .setJournalpostId(journalpostId)
                        .setDokumentListe(listOf(Dokument.newBuilder().setDokumentId("123").build()))
                        .build()
                )
                .setTrengerManuellBehandling(false)
                .build()
            val record = behovProducer.send(ProducerRecord(Topics.INNGÅENDE_JOURNALPOST.name, innkommendeBehov)).get()
            LOGGER.info { "Produced -> ${record.topic()}  to offset ${record.offset()}" }
        }


        TimeUnit.SECONDS.sleep(3) // awaiting event processing

        assertEquals(
            henvendelsesType.values.filter { it == Søknad() || it == Ettersending() }.size,
            oppslagClient.ferdigstillTeller,
            "Should have have processed Behov and 'ferdigstilt' journalpost"
        )

        journalføringFerdigstill.stop()
    }

    private fun behovProducer(env: Environment): KafkaProducer<String, Behov> {
        val producer: KafkaProducer<String, Behov> = KafkaProducer(Properties().apply {
            put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, env.schemaRegistryUrl)
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, env.bootstrapServersUrl)
            put(ProducerConfig.CLIENT_ID_CONFIG, "dummy-behov-producer")
            put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                Topics.INNGÅENDE_JOURNALPOST.keySerde.serializer().javaClass.name
            )
            put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                Topics.INNGÅENDE_JOURNALPOST.valueSerde.serializer().javaClass.name
            )
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT")
            put(SaslConfigs.SASL_MECHANISM, "PLAIN")
            put(
                SaslConfigs.SASL_JAAS_CONFIG,
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${env.username}\" password=\"${env.password}\";"
            )
        })

        return producer
    }

    class DummyOppslagsClient(val knownedJournalPosts: Set<String>) : OppslagClient {

        var ferdigstillTeller = 0

        override fun ferdigstillJournalføring(journalpostId: String) {
            assertTrue(knownedJournalPosts.contains(journalpostId), "Journalpost not knowned")
            ferdigstillTeller++
        }
    }
}