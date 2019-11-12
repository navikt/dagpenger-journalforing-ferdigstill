package no.nav.dagpenger.journalføring.ferdigstill

import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.FNR
import org.apache.kafka.streams.kstream.Predicate

internal object PacketKeys {
    const val JOURNALPOST_ID: String = "journalpostId"
    const val FNR: String = "aktørId"
    const val ARENA_SAK_RESULTAT: String = "arenaSakResultat"
    const val ARENA_SAK_ID: String = "arenaSakId"
}

internal val filterPredicates = listOf<Predicate<String, Packet>>(
    Predicate { _, packet -> packet.hasField(PacketKeys.ARENA_SAK_ID) || packet.hasField(PacketKeys.ARENA_SAK_RESULTAT) })

internal class JournalFøringFerdigstill(private val journalPostApi: JournalPostApi) {
    fun handlePacket(packet: Packet) {
        journalPostApi.oppdater(packet.getStringValue(FNR), packet.getStringValue(PacketKeys.JOURNALPOST_ID))
        journalPostApi.ferdigstill(packet.getStringValue(PacketKeys.JOURNALPOST_ID))
    }
}
