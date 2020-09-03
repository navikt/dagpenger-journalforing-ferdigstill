package no.nav.dagpenger.journalføring.ferdigstill
import java.time.LocalDateTime

class MedlemskapBehovRiver {
    fun opprettBehov(s: String): Medlemskapsevaluering {
        TODO("not implemented")
    }
}

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
