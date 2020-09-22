package no.nav.dagpenger.journalføring.ferdigstill

import com.squareup.moshi.Types
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.events.moshiInstance
import no.nav.dagpenger.journalføring.ferdigstill.adapter.Avsender
import no.nav.dagpenger.journalføring.ferdigstill.adapter.Bruker
import no.nav.dagpenger.journalføring.ferdigstill.adapter.Dokument
import no.nav.dagpenger.journalføring.ferdigstill.adapter.OppdaterJournalpostPayload
import no.nav.dagpenger.journalføring.ferdigstill.adapter.Sak
import no.nav.dagpenger.journalføring.ferdigstill.adapter.SaksType
import java.time.LocalDateTime
import java.time.ZoneId

internal fun Packet.harInntektFraFangstOgFiske(): Boolean =
    this.getSøknad()?.getBooleanFaktum("egennaering.fangstogfiske", false) ?: false

internal object PacketMapper {
    val dokumentJsonAdapter = moshiInstance.adapter<List<Dokument>>(
        Types.newParameterizedType(
            List::class.java,
            Dokument::class.java
        )
    ).lenient()

    fun journalpostIdFrom(packet: Packet) = packet.getStringValue(PacketKeys.JOURNALPOST_ID)
    fun avsenderFrom(packet: Packet) =
        Avsender(packet.getStringValue(PacketKeys.AVSENDER_NAVN))

    fun bruker(packet: Packet) =
        Bruker(packet.getStringValue(PacketKeys.NATURLIG_IDENT))

    fun hasNaturligIdent(packet: Packet) = packet.hasField(PacketKeys.NATURLIG_IDENT)
    fun nullableAktørFrom(packet: Packet) =
        if (packet.hasField(PacketKeys.AKTØR_ID)) Bruker(
            packet.getStringValue(PacketKeys.AKTØR_ID),
            "AKTØR"
        ) else null

    fun aktørFrom(packet: Packet) = Bruker(
        packet.getStringValue(PacketKeys.AKTØR_ID),
        "AKTØR"
    )

    data class OppgaveBenk(
        val id: String,
        val beskrivelse: String
    )

    fun oppgaveBeskrivelseOgBenk(packet: Packet): OppgaveBenk {
        val kanAvslåsPåMinsteinntekt = packet.getNullableBoolean(PacketKeys.OPPFYLLER_MINSTEINNTEKT) == false
        val koronaRegelverkMinsteinntektBrukt =
            packet.getNullableBoolean(PacketKeys.KORONAREGELVERK_MINSTEINNTEKT_BRUKT) == true
        val konkurs = packet.harAvsluttetArbeidsforholdFraKonkurs()
        val grenseArbeider = packet.erGrenseArbeider()
        val inntektFraFangstFisk = packet.harInntektFraFangstOgFiske()

        return when {
            inntektFraFangstFisk -> OppgaveBenk(packet.getStringValue(PacketKeys.BEHANDLENDE_ENHET), "FANGST OG FISKE\n")
            grenseArbeider -> OppgaveBenk("4470", "SAMMENLEGGINGSSAKER\n")
            konkurs -> OppgaveBenk("4450", "Konkurs\n")
            kanAvslåsPåMinsteinntekt -> OppgaveBenk(packet.finnEnhetForHurtigAvslag(), if (koronaRegelverkMinsteinntektBrukt) "Minsteinntekt - mulig avslag - korona\n" else "Minsteinntekt - mulig avslag\n")
            else -> OppgaveBenk(tildeltEnhetsNrFrom(packet), henvendelse(packet).oppgavebeskrivelse)
        }
    }

    private fun Packet.finnEnhetForHurtigAvslag() = when (this.getStringValue(PacketKeys.BEHANDLENDE_ENHET)) {
        "4450" -> ENHET_FOR_HURTIG_AVSLAG_IKKE_PERMITTERT
        "4455" -> ENHET_FOR_HURTIG_AVSLAG_PERMITTERT
        else -> this.getStringValue(PacketKeys.BEHANDLENDE_ENHET)
    }

    fun henvendelse(packet: Packet): Henvendelse {
        return Henvendelse.fra(packet.getStringValue(PacketKeys.HENVENDELSESTYPE))
    }

    fun harFagsakId(packet: Packet): Boolean = packet.hasField(PacketKeys.FAGSAK_ID)
    fun harIkkeFagsakId(packet: Packet): Boolean = !harFagsakId(packet)

    fun tildeltEnhetsNrFrom(packet: Packet) = packet.getStringValue(PacketKeys.BEHANDLENDE_ENHET)
    fun dokumenterFrom(packet: Packet) = packet.getObjectValue(PacketKeys.DOKUMENTER) {
        dokumentJsonAdapter.fromJsonValue(it)!!
    }

    fun registrertDatoFrom(packet: Packet) =
        LocalDateTime.parse(packet.getStringValue(PacketKeys.DATO_REGISTRERT)).atZone(ZoneId.of("Europe/Oslo"))

    fun dokumentTitlerFrom(packet: Packet) =
        packet.getObjectValue(PacketKeys.DOKUMENTER) { dokumentJsonAdapter.fromJsonValue(it)!! }.map { it.tittel }

    fun tittelFrom(packet: Packet) = dokumenterFrom(packet).first().tittel

    fun sakFrom(fagsakId: FagsakId?) = if (fagsakId != null) Sak(
        saksType = SaksType.FAGSAK,
        fagsaksystem = "AO01",
        fagsakId = fagsakId.value
    ) else Sak(
        SaksType.GENERELL_SAK,
        null,
        null
    )

    fun journalPostFrom(packet: Packet, fagsakId: FagsakId?): OppdaterJournalpostPayload {
        return OppdaterJournalpostPayload(
            avsenderMottaker = avsenderFrom(packet),
            bruker = bruker(packet),
            tittel = tittelFrom(packet),
            sak = sakFrom(fagsakId),
            dokumenter = dokumenterFrom(packet)
        )
    }

    fun hasAktørId(packet: Packet) = packet.hasField(PacketKeys.AKTØR_ID)
}
