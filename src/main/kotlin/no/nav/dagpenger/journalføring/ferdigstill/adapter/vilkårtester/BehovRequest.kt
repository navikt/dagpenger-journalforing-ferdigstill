package no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester

import java.time.LocalDate

data class BehovRequest(
    val aktorId: String,
    val regelkontekst: BehovClient.RegelKontekst,
    val beregningsdato: LocalDate,
    val vedtakId: Int = -12345, // todo: fjerne vedtakId
    val harAvtjentVerneplikt: Boolean? = null,
    val oppfyllerKravTilFangstOgFisk: Boolean? = null,
    val bruktInntektsPeriode: LocalDate? = null,
    val manueltGrunnlag: Int? = null,
    val antallBarn: Int? = null,
    val inntektsId: String? = null
)
