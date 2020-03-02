package no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester

import no.nav.dagpenger.events.inntekt.v1.Inntekt
import java.math.BigDecimal

data class Subsumsjon(
    val behovId: String,
    val grunnlagResultat: GrunnlagResultat?,
    val minsteinntektResultat: MinsteinntektResultat?,
    val periodeResultat: PeriodeResultat?,
    val satsResultat: SatsResultat?
)

data class GrunnlagResultat(
    val subsumsjonsId: String,
    val sporingsId: String,
    val regelIdentifikator: String,
    val avkortet: BigDecimal,
    val uavkortet: BigDecimal,
    val harAvkortet: Boolean,
    val beregningsregel: String,
    val grunnlagInntektsPerioder: List<Inntekt>?
)

data class MinsteinntektResultat(
    val subsumsjonsId: String,
    val sporingsId: String,
    val oppfyllerMinsteinntekt: Boolean,
    val regelIdentifikator: String,
    val minsteinntektInntektsPerioder: List<Inntekt>
)

data class PeriodeResultat(
    val subsumsjonsId: String,
    val sporingsId: String,
    val regelIdentifikator: String,
    val periodeAntallUker: Int
)

data class SatsResultat(
    val subsumsjonsId: String,
    val sporingsId: String,
    val regelIdentifikator: String,
    val dagsats: Int,
    val ukesats: Int,
    val benyttet90ProsentRegel: Boolean
)