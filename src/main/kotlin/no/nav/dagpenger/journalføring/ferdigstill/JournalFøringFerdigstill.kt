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

internal class JournalFøringFerdigstill(private val journalPostApi: JournalPostApi) {
    fun handlePacket(packet: Packet) {
        journalPostApi.oppdater(packet.getStringValue(PacketKeys.JOURNALPOST_ID), velgJson(packet))
        journalPostApi.ferdigstill(packet.getStringValue(PacketKeys.JOURNALPOST_ID))
    }

    fun velgJson(packet: Packet): String {
        val arenaSakFinnes = packet.hasField(PacketKeys.ARENA_SAK_ID)
        return if (arenaSakFinnes) {
            arenaJson(packet.getStringValue(FNR), packet.getStringValue(PacketKeys.ARENA_SAK_ID))
        } else {
            generellSakJson(packet.getStringValue(FNR))
        }
    }

    fun arenaJson(fnr: String, fagsakId: String): String {
        return """{
  "bruker": {
    "id": "$fnr",
    "idType": "FNR"
  },
  "tema": "DAG",
  "behandlingstema": "ab0001",
  "journalfoerendeEnhet": "9999",
  "sak": {
    "sakstype": "FAGSAK",
    "fagsaksystem": "AO01",
    "fagsakId": "$fagsakId"
  }
}""".trimIndent()
    }

    fun generellSakJson(fnr: String): String {
        return """{
  "bruker": {
    "id": "$fnr",
    "idType": "FNR"
  },
  "tema": "DAG",
  "behandlingstema": "ab0001",
  "journalfoerendeEnhet": "9999",
  "sak": {
    "sakstype": "GENERELL_SAK"
  }
}""".trimIndent()
    }
}
