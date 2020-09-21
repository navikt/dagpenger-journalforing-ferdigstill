package no.nav.dagpenger.journalføring.ferdigstill

import no.nav.dagpenger.events.Packet

internal typealias AvsluttedeArbeidsforhold = List<AvsluttetArbeidsforhold>

fun Packet.avsluttetArbeidsforhold(): AvsluttedeArbeidsforhold {
    return this.getSøknad()?.let { søknad ->
        søknad.getFakta("arbeidsforhold").map {
            AvsluttetArbeidsforhold(
                sluttårsak = asÅrsak(it["properties"]["type"].asText()),
                grensearbeider = !søknad.getBooleanFaktum("arbeidsforhold.grensearbeider", true),
            )
        }
    } ?: emptyList()
}

fun Packet.erGrenseArbeider(): Boolean =
    this.avsluttetArbeidsforhold().any { it.grensearbeider }

fun Packet.harAvsluttetArbeidsforholdFraKonkurs(): Boolean =
    this.avsluttetArbeidsforhold().any { it.sluttårsak == AvsluttetArbeidsforhold.Sluttårsak.ARBEIDSGIVER_KONKURS }

data class AvsluttetArbeidsforhold(
    val sluttårsak: Sluttårsak,
    val grensearbeider: Boolean,
) {
    enum class Sluttårsak {
        AVSKJEDIGET,
        ARBEIDSGIVER_KONKURS,
        KONTRAKT_UTGAATT,
        PERMITTERT,
        REDUSERT_ARBEIDSTID,
        SAGT_OPP_AV_ARBEIDSGIVER,
        SAGT_OPP_SELV
    }
}

private fun asÅrsak(type: String): AvsluttetArbeidsforhold.Sluttårsak = when (type) {
    "permittert" -> AvsluttetArbeidsforhold.Sluttårsak.PERMITTERT
    "avskjediget" -> AvsluttetArbeidsforhold.Sluttårsak.AVSKJEDIGET
    "kontraktutgaatt" -> AvsluttetArbeidsforhold.Sluttårsak.KONTRAKT_UTGAATT
    "redusertarbeidstid" -> AvsluttetArbeidsforhold.Sluttårsak.REDUSERT_ARBEIDSTID
    "sagtoppavarbeidsgiver" -> AvsluttetArbeidsforhold.Sluttårsak.SAGT_OPP_AV_ARBEIDSGIVER
    "sagtoppselv" -> AvsluttetArbeidsforhold.Sluttårsak.SAGT_OPP_SELV
    "arbeidsgivererkonkurs" -> AvsluttetArbeidsforhold.Sluttårsak.ARBEIDSGIVER_KONKURS
    else -> throw Exception("Missing permitteringstype")
}
