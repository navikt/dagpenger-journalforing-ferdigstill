package no.nav.dagpenger.journalføring.ferdigstill
import no.nav.helse.rapids_rivers.JsonMessage
import java.time.LocalDate

class MedlemskapBehovRiver(val behovRiver: BehovRiver) {

    suspend fun hentSvar(fnr: String, beregningsdato: LocalDate, journalpostId: String): Medlemskapstatus {
        val id = behovRiver.opprettBehov(
            mapOf(
                "fødselsnummer" to fnr,
                "beregningsdato" to beregningsdato.toString(),
                "vedtakid" to journalpostId
            )
        )

        return behovRiver.hentSvar(id) {
            message: JsonMessage ->
            message["@løsning"]["Medlemskap"]["resultat"]["svar"].asText().let {
                when (it) {
                    "JA" -> Medlemskapstatus.JA
                    "NEI" -> Medlemskapstatus.NEI
                    else -> Medlemskapstatus.VET_IKKE
                }
            }
        }
    }
}

enum class Medlemskapstatus {
    JA, NEI, VET_IKKE
}
