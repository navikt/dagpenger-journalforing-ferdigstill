package no.nav.dagpenger.journalføring.opprydder

import mu.KotlinLogging
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.Configuration
import no.nav.dagpenger.journalføring.ferdigstill.ManuellJournalføringsBehandlingsChain
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys
import no.nav.dagpenger.journalføring.ferdigstill.adapter.GosysOppgaveClient
import no.nav.dagpenger.streams.Pond
import no.nav.dagpenger.streams.streamConfig
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.Predicate
import org.apache.logging.log4j.ThreadContext
import java.util.Properties

private val logger = KotlinLogging.logger {}

internal class OpprydderApp(
    private val configuration: Configuration,
    private val gosysOppgaveClient: GosysOppgaveClient
) : Pond(configuration.kafka.dagpengerJournalpostTopic) {

    override val SERVICE_APP_ID = "dp-opprydder-manuell-behandling-dry-run-v4"
    override val HTTP_PORT: Int = 8079
    override val withHealthChecks: Boolean
        get() = false

    val manuellOppgaveChain = ManuellJournalføringsBehandlingsChain(gosysOppgaveClient, null)

    override fun filterPredicates() = listOf(erFerdigBehandletJournalpostSomSkalFikses)

    internal val erFerdigBehandletJournalpostSomSkalFikses = Predicate<String, Packet> { _, packet ->
        packet.hasField(PacketKeys.JOURNALPOST_ID) &&
            packet.getNullableBoolean(PacketKeys.FERDIG_BEHANDLET) == true &&
            packet.getStringValue(PacketKeys.JOURNALPOST_ID) in fiksDisseJournalpostene
    }

    override fun onPacket(packet: Packet) {
        try {
            ThreadContext.putAll(mapOf(
                "x_journalpost_id" to packet.getStringValue(PacketKeys.JOURNALPOST_ID),
                "x_dry_run" to "true")
            )
            logger.info { "Opprydder besøker ${packet.getStringValue(PacketKeys.JOURNALPOST_ID)}, henvendelsestype: ${packet.getStringValue(PacketKeys.HENVENDELSESTYPE)}" }
        } finally {
            ThreadContext.removeAll(listOf("x_journalpost_id", "x_dry_run"))
        }
    }

    override fun getConfig(): Properties {
        val properties = streamConfig(
            SERVICE_APP_ID,
            configuration.kafka.brokers,
            configuration.kafka.credential
        )
        properties[StreamsConfig.PROCESSING_GUARANTEE_CONFIG] = configuration.kafka.processingGuarantee
        return properties
    }
}