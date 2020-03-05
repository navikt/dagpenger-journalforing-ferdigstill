package no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester

import java.math.BigDecimal
import java.time.YearMonth

data class Subsumsjon(
    val behovId: String,
    val minsteinntektResultat: MinsteinntektResultat?
)

data class MinsteinntektResultat(
    val subsumsjonsId: String,
    val sporingsId: String,
    val oppfyllerMinsteinntekt: Boolean,
    val regelIdentifikator: String,
    val minsteinntektInntektsPerioder: List<Inntekt>
)

data class Inntekt(
    val inntekt: BigDecimal,
    val periode: Int, // todo: enum?
    val inntektsPeriode: InntektsPeriode,
    val inneholderFangstOgFisk: Boolean,
    val andel: BigDecimal? = null
) {
    init {
        val gyldigePerioder = setOf(1, 2, 3)
        if (!gyldigePerioder.contains(periode)) {
            throw IllegalArgumentException("Ugyldig periode for inntektgrunnlat, gyldige verdier er ${gyldigePerioder.joinToString { "$it" }}")
        }
    }
}

data class InntektsPeriode(
    val førsteMåned: YearMonth,
    val sisteMåned: YearMonth
)
