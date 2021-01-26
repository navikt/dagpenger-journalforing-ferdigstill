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
    this.getSøknad()?.getBooleanFaktum("egennaering.fangstogfiske", false)?.not() ?: false

internal fun Packet.harEøsArbeidsforhold(): Boolean =
    this.getSøknad()?.getBooleanFaktum("eosarbeidsforhold.jobbetieos", true)?.not() ?: false

internal fun Packet.harAvtjentVerneplikt(): Boolean =
    getSøknad()?.getFakta("ikkeavtjentverneplikt")?.getOrNull(0)?.get("value")?.asBoolean()?.not() ?: false

typealias ReellArbeidssøker = Map<String, Boolean?>

internal val ReellArbeidssøker.villigAlle get() = values.all { it ?: false }

internal fun Packet.reellArbeidssøker(): ReellArbeidssøker =
    mapOf(
        "villigdeltid" to getSøknad()?.getFakta("reellarbeidssoker.villigdeltid")?.getOrNull(0)?.get("value")
            ?.asBoolean(),
        "villigpendle" to getSøknad()?.getFakta("reellarbeidssoker.villigpendle")?.getOrNull(0)?.get("value")
            ?.asBoolean(),
        "villighelse" to getSøknad()?.getFakta("reellarbeidssoker.villighelse")?.getOrNull(0)?.get("value")
            ?.asBoolean(),
        "villigjobb" to getSøknad()?.getFakta("reellarbeidssoker.villigjobb")?.getOrNull(0)?.get("value")?.asBoolean()
    )

internal fun Packet.språk(): String? =
    getSøknad()?.getFakta("skjema.sprak")?.getOrNull(0)?.get("value")?.asText()

internal fun Packet.antallArbeidsforhold(): Int? =
    getSøknad()?.getFakta("arbeidsforhold")?.size

internal fun Packet.andreYtelser(): Boolean? =
    getSøknad()?.getFakta("andreytelser.ytelser.ingenytelse")?.getOrNull(0)?.get("value")?.asBoolean()?.not()

internal fun Packet.verneplikt(): Boolean? =
    getSøknad()?.getFakta("ikkeavtjentverneplikt")?.getOrNull(0)?.get("value")?.asBoolean()?.not()

internal fun Packet.egenNæring() =
    getSøknad()?.getFakta("egennaering.driveregennaering")?.getOrNull(0)?.get("value")?.asBoolean()?.not()

internal fun Packet.gårdsbruk() =
    getSøknad()?.getFakta("egennaering.gardsbruk")?.getOrNull(0)?.get("value")?.asBoolean()?.not()

internal fun Packet.fangstOgFiske() =
    getSøknad()?.getFakta("egennaering.fangstogfiske")?.getOrNull(0)?.get("value")?.asBoolean()?.not()

internal fun Packet.arbeidstilstand(): String? =
    getSøknad()?.getFakta("arbeidsforhold.arbeidstilstand")?.getOrNull(0)?.get("value")?.asText()

internal fun Packet.utdanning(): String? =
    getSøknad()?.getFakta("utdanning")?.getOrNull(0)?.get("value")?.asText()

internal fun Packet.fornyetRettighet(): Boolean =
    getSøknad()?.getFakta("fornyetrett")?.getOrNull(0)?.get("value")?.asText() == "ja"

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
        val eøsArbeidsforhold = packet.harEøsArbeidsforhold()
        val inntektFraFangstFisk = packet.harInntektFraFangstOgFiske()
        val harAvtjentVerneplikt = packet.harAvtjentVerneplikt()
        val erPermittertFraFiskeforedling = packet.erPermittertFraFiskeForedling()
        val diskresjonskodeBenk = packet.getStringValue(PacketKeys.BEHANDLENDE_ENHET) == "2103"

        return when {
            diskresjonskodeBenk -> OppgaveBenk(tildeltEnhetsNrFrom(packet), henvendelse(packet).oppgavebeskrivelse)
            eøsArbeidsforhold -> OppgaveBenk("4470", "MULIG SAMMENLEGGING - EØS\n")
            harAvtjentVerneplikt -> OppgaveBenk(packet.getStringValue(PacketKeys.BEHANDLENDE_ENHET), "VERNEPLIKT\n")
            inntektFraFangstFisk -> OppgaveBenk(
                packet.getStringValue(PacketKeys.BEHANDLENDE_ENHET),
                "FANGST OG FISKE\n"
            )
            grenseArbeider -> OppgaveBenk("4465", "EØS\n")
            konkurs -> OppgaveBenk("4401", "Konkurs\n")
            erPermittertFraFiskeforedling -> OppgaveBenk("4454", "FISK\n")
            kanAvslåsPåMinsteinntekt -> OppgaveBenk(
                packet.finnEnhetForHurtigAvslag(),
                if (koronaRegelverkMinsteinntektBrukt) "Minsteinntekt - mulig avslag - korona\n" else "Minsteinntekt - mulig avslag\n"
            )
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
