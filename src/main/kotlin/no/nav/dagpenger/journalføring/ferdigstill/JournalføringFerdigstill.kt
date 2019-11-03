package no.nav.dagpenger.journalføring.ferdigstill

import mu.KotlinLogging
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.streams.River
import no.nav.dagpenger.streams.streamConfig
import org.apache.kafka.streams.kstream.Predicate
import java.util.Properties

private val logger = KotlinLogging.logger {}

class JournalføringFerdigstill(val configuration: Configuration) : River(configuration.kafka.dagpengerJournalpostTopic) {

    override val SERVICE_APP_ID = "dagpenger-journalføring-ferdigstill"
    override val HTTP_PORT: Int = configuration.application.httpPort

    override fun filterPredicates(): List<Predicate<String, Packet>> {
        return listOf(
            Predicate { _, packet -> !packet.hasField("dagpenger-journalføring-ferdigstill") }
        )
    }

    override fun onPacket(packet: Packet): Packet {

        logger.info { "GOT VALUE $packet" }

        packet.putValue("dagpenger-journalføring-ferdigstill", "yes")
        return packet
    }

    override fun getConfig(): Properties {
        return streamConfig(
            SERVICE_APP_ID,
            configuration.kafka.brokers,
            configuration.kafka.credential
        )
    }
}

fun main() = JournalføringFerdigstill(Configuration()).start()
