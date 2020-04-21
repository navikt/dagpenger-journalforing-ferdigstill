package no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester

import java.time.LocalDate

data class BehovRequest(
    val aktorId: String,
    val vedtakId: Int,
    val beregningsdato: LocalDate,
    val harAvtjentVerneplikt: Boolean? = null,
    val oppfyllerKravTilFangstOgFisk: Boolean? = null,
    val bruktInntektsPeriode: LocalDate? = null,
    val manueltGrunnlag: Int? = null,
    val antallBarn: Int? = null,
    val inntektsId: String? = null
)
