package no.nav.dagpenger.journalføring.ferdigstill

import mu.KotlinLogging
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.streams.Pond
import no.nav.dagpenger.streams.streamConfig
import org.apache.kafka.streams.kstream.Predicate
import java.util.Properties

private val logger = KotlinLogging.logger {}

internal class Application(val configuration: Configuration) : Pond(configuration.kafka.dagpengerJournalpostTopic) {

    override val SERVICE_APP_ID = "dagpenger-journalføring-ferdigstill"
    override val HTTP_PORT: Int = configuration.application.httpPort

    private val journalFøringFerdigstill = JournalFøringFerdigstill(JournalPostRestApi(configuration.journalPostApiUrl))

    override fun filterPredicates(): List<Predicate<String, Packet>> = filterPredicates

    override fun onPacket(packet: Packet) {
        logger.info { "Processing: $packet" }.also { journalFøringFerdigstill.handlePacket(packet) }
    }

    override fun getConfig(): Properties {
        return streamConfig(
            SERVICE_APP_ID,
            configuration.kafka.brokers,
            configuration.kafka.credential
        )
    }
}

fun main() = Application(Configuration()).start()
