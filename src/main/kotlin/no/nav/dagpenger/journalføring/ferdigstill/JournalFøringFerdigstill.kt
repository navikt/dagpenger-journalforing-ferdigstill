package no.nav.dagpenger.journalføring.ferdigstill

import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.FNR
import org.apache.kafka.streams.kstream.Predicate

internal object PacketKeys {
    const val JOURNALPOST_ID: String = "journalpostId"
    const val FNR: String = "naturligIdent"
    const val ARENA_SAK_OPPRETTET: String = "arenaSakOpprettet"
    const val ARENA_SAK_ID: String = "arenaSakId"
}

internal val isJournalFørt = Predicate<String, Packet> { _, packet ->
    packet.hasField(PacketKeys.ARENA_SAK_ID) && packet.hasField(PacketKeys.ARENA_SAK_OPPRETTET) &&
        packet.hasField(FNR) && packet.hasField(PacketKeys.JOURNALPOST_ID)
}

internal class JournalFøringFerdigstill(private val journalPostApi: JournalPostApi) {
    fun handlePacket(packet: Packet) {
        journalPostApi.oppdater(packet.getStringValue(FNR), packet.getStringValue(PacketKeys.JOURNALPOST_ID), packet.getStringValue(PacketKeys.ARENA_SAK_ID))
        journalPostApi.ferdigstill(packet.getStringValue(PacketKeys.JOURNALPOST_ID))
    }
}
