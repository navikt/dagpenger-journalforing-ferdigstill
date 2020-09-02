package no.nav.dagpenger.journalføring.ferdigstill

import java.time.LocalDateTime

data class Medlemskapsevaluering(
    val tidspunkt: LocalDateTime,
    val versjonTjeneste: String,
    val versjonRegler: String,
    val datagrunnlag: Map<String, Any>,
    val resultat: Medlemskapresultat
)

data class Medlemskapresultat(
    val identifikator: String?,
    val avklaring: String,
    val begrunnelse: String,
    val svar: String,
    val delresultat: List<Medlemskapresultat>
)
