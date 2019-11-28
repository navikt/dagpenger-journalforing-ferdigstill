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
    packet.hasField(PacketKeys.ARENA_SAK_OPPRETTET) &&
        packet.hasField(FNR) && packet.hasField(PacketKeys.JOURNALPOST_ID)
}

internal interface Sak {
    fun toJsonString(): String
}

internal data class GenerellSak(private val packet: Packet) : Sak {
    override fun toJsonString(): String = packet.getStringValue(FNR).let { fnr ->
        return """
        {
           "bruker": {
            "id": "$fnr",
              "idType": "FNR"
            },
            "behandlingstema": "ab0001",
            "tema": "DAG",
            "journalfoerendeEnhet": "9999",
            "sak": {
               "sakstype": "GENERELL_SAK"
             }
        }"""
    }
}

internal data class ArenaSak(private val packet: Packet) : Sak {
    override fun toJsonString(): String {
        val fnr = packet.getStringValue(FNR)
        val arenaSakId = packet.getStringValue(PacketKeys.ARENA_SAK_ID)
        return """
        {
           "bruker": {
            "id": "$fnr",
              "idType": "FNR"
            },
            "behandlingstema": "ab0001",
            "tema": "DAG",
            "journalfoerendeEnhet": "9999",
            "sak": {
               "sakstype": "FAGSAK",
               "fagsaksystem": "AO01",
               "fagsakId": "$arenaSakId"
             }
        }"""
    }
}

internal class JournalFøringFerdigstill(private val journalPostApi: JournalPostApi) {
    fun handlePacket(packet: Packet) {
        journalPostApi.oppdater(packet.getStringValue(PacketKeys.JOURNALPOST_ID), velgSaksType(packet))
        journalPostApi.ferdigstill(packet.getStringValue(PacketKeys.JOURNALPOST_ID))
    }

    private fun velgSaksType(packet: Packet): Sak {
        return when (packet.hasField(PacketKeys.ARENA_SAK_ID)) {
            true -> ArenaSak(packet)
            else -> GenerellSak(packet)
        }
    }
}
