package no.nav.dagpenger.journalføring.ferdigstill
import java.time.LocalDate
import java.time.LocalDateTime

class MedlemskapBehovRiver {

    fun hentSvar(fnr: String, beregningsdato: LocalDate, vedtakId: String): Medlemskapsevaluering {
        TODO("not implemented")
    }

    // / val medlemskap = BehovRiver(rapidsConnection, listOf(Behov.Medlemskap),(JsonMessage) -> String)
    // / val soknadfakta = BehovRiver(rapidsConnection, listOf(Behov.Verneplikt, Behov.Registrering),(JsonMessage) -> String)
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
