package no.nav.dagpenger.journalføring.ferdigstill

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.PacketKeys.FNR
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.journalPostFrom
import org.apache.kafka.streams.kstream.Predicate

internal val isJournalFørt = Predicate<String, Packet> { _, packet ->
    packet.hasField(PacketKeys.ARENA_SAK_OPPRETTET) &&
        packet.hasField(FNR) &&
        packet.hasField(PacketKeys.JOURNALPOST_ID) &&
        packet.hasField(PacketKeys.AVSENDER_NAVN) &&
        packet.hasField(PacketKeys.DOKUMENTER)
}

internal object PacketToJoarkPayloadMapper {
    private val dokumentJsonAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build().adapter<List<Dokument>>(
            Types.newParameterizedType(
                List::class.java,
                Dokument::class.java
            )
        )

    fun journalPostIdFrom(packet: Packet) = packet.getStringValue(PacketKeys.JOURNALPOST_ID)
    fun avsenderFrom(packet: Packet) = Avsender(packet.getStringValue(PacketKeys.AVSENDER_NAVN))
    fun brukerFrom(packet: Packet) = Bruker(packet.getStringValue(FNR))
    fun dokumenterFrom(packet: Packet) = packet.getStringValue(PacketKeys.DOKUMENTER).let {
        dokumentJsonAdapter.fromJson(it)!!
    }

    fun tittelFrom(packet: Packet) = dokumenterFrom(packet).first().tittel
    fun sakFrom(packet: Packet) = when (packet.hasField(PacketKeys.ARENA_SAK_ID)) {
        true -> Sak(
            saksType = SaksType.FAGSAK,
            fagsaksystem = "AO01",
            fagsakId = packet.getStringValue(PacketKeys.ARENA_SAK_ID))
        else -> Sak(SaksType.GENERELL_SAK, null, null)
    }

    fun journalPostFrom(packet: Packet): OppdaterJournalPostPayload {
        return OppdaterJournalPostPayload(
            avsenderMottaker = avsenderFrom(packet),
            bruker = brukerFrom(packet),
            tittel = tittelFrom(packet),
            sak = sakFrom(packet),
            dokumenter = dokumenterFrom(packet)
        )
    }
}

internal class JournalFøringFerdigstill(private val journalPostApi: JournalPostApi) {
    fun handlePacket(packet: Packet) {
        journalPostApi.oppdater(packet.getStringValue(PacketKeys.JOURNALPOST_ID), journalPostFrom(packet))
        journalPostApi.ferdigstill(packet.getStringValue(PacketKeys.JOURNALPOST_ID))
    }
}
