package no.nav.dagpenger.journalføring.ferdigstill

import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig
import mu.KotlinLogging
import no.nav.dagpenger.events.avro.Behov
import no.nav.dagpenger.events.avro.JournalpostType
import no.nav.dagpenger.streams.KafkaCredential
import no.nav.dagpenger.streams.Service
import no.nav.dagpenger.streams.Topics.INNGÅENDE_JOURNALPOST
import no.nav.dagpenger.streams.configureAvroSerde
import no.nav.dagpenger.streams.consumeTopic
import no.nav.dagpenger.streams.streamConfig
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import java.util.Properties

private val LOGGER = KotlinLogging.logger {}

class JournalføringFerdigstill(val env: Environment, private val oppslagHttpClient: OppslagHttpClient) : Service() {
    override val SERVICE_APP_ID = "journalføring-ferdigstill" // NB: also used as group.id for the consumer group - do not change!

    override val HTTP_PORT: Int = env.httpPort ?: super.HTTP_PORT

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val env = Environment()
            val service = JournalføringFerdigstill(env, OppslagHttpClient(env.dagpengerOppslagUrl))
            service.start()
        }
    }

    override fun setupStreams(): KafkaStreams {
        LOGGER.info { "Initiating start of $SERVICE_APP_ID" }
        val innkommendeJournalpost = INNGÅENDE_JOURNALPOST.copy(
                valueSerde = configureAvroSerde<Behov>(
                        mapOf(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG to env.schemaRegistryUrl)
                )
        )

        val builder = StreamsBuilder()
        val inngåendeJournalposter = builder.consumeTopic(innkommendeJournalpost)

        inngåendeJournalposter
                .peek { key, value -> LOGGER.info("Processing ${value.javaClass} with key $key") }
                .filter { _, behov -> behov.getJournalpost().getGsaksakId() != null }
                .filter { _, behov -> filterJournalpostTypes(behov.getJournalpost().getJournalpostType()) }
                .foreach { _, value -> ferdigstillJournalføring(value) }

        return KafkaStreams(builder.build(), this.getConfig())
    }

    private fun filterJournalpostTypes(journalpostType: JournalpostType): Boolean {
        return when (journalpostType) {
            JournalpostType.NY, JournalpostType.GJENOPPTAK, JournalpostType.ETTERSENDING -> true
            JournalpostType.UKJENT, JournalpostType.MANUELL -> false
        }
    }

    fun ferdigstillJournalføring(behov: Behov) {
        oppslagHttpClient.ferdigstillJournalføring(behov.getJournalpost().getJournalpostId())
    }

    override fun getConfig(): Properties {
        return streamConfig(appId = SERVICE_APP_ID, bootStapServerUrl = env.bootstrapServersUrl, credential = KafkaCredential(env.username, env.password))
    }
}