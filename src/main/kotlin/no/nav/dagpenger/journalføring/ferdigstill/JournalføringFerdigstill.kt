package no.nav.dagpenger.journalføring.ferdigstill

import mu.KotlinLogging
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.streams.River
import org.apache.kafka.streams.kstream.Predicate

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
}

fun main() = JournalføringFerdigstill(Configuration()).start()
