package no.nav.dagpenger.journalføring.ferdigstill

import mu.KotlinLogging
import no.nav.dagpenger.events.avro.Behov
import no.nav.dagpenger.events.hasFagsakId
import no.nav.dagpenger.events.isEttersending
import no.nav.dagpenger.events.isSoknad
import no.nav.dagpenger.streams.KafkaCredential
import no.nav.dagpenger.streams.Service
import no.nav.dagpenger.streams.Topics.INNGÅENDE_JOURNALPOST
import no.nav.dagpenger.streams.consumeTopic
import no.nav.dagpenger.streams.streamConfig
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import java.util.Properties

private val LOGGER = KotlinLogging.logger {}

class JournalføringFerdigstill(val env: Environment, private val oppslagClient: OppslagClient) : Service() {
    override val SERVICE_APP_ID =
        "journalføring-ferdigstill" // NB: also used as group.id for the consumer group - do not change!

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

        val builder = StreamsBuilder()
        val inngåendeJournalposter = builder.consumeTopic(INNGÅENDE_JOURNALPOST, env.schemaRegistryUrl)

        inngåendeJournalposter
            .peek { key, value -> LOGGER.info("Processing ${value.javaClass} with key $key") }
            .filter { _, behov -> behov.hasFagsakId() }
            .filter { _, behov -> filterHenvendelsesType(behov) }
            .foreach { _, value -> ferdigstillJournalføring(value) }

        return KafkaStreams(builder.build(), this.getConfig())
    }

    private fun filterHenvendelsesType(behov: Behov): Boolean {
        return behov.isSoknad() || behov.isEttersending()
    }

    fun ferdigstillJournalføring(behov: Behov) {
        oppslagClient.ferdigstillJournalføring(behov.getJournalpost().getJournalpostId())
    }

    override fun getConfig(): Properties {
        return streamConfig(
            appId = SERVICE_APP_ID,
            bootStapServerUrl = env.bootstrapServersUrl,
            credential = KafkaCredential(env.username, env.password)
        )
    }
}