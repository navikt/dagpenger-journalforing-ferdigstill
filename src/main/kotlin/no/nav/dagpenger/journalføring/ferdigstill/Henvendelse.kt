package no.nav.dagpenger.journalføring.ferdigstill


sealed class Henvendelse(val oppgavebeskrivelse: String) {

    companion object {

        fun fra(henvendelsesnavn: String): Henvendelse {
            return when (henvendelsesnavn) {
                "NY_SØKNAD" -> NyttSaksforhold
                "ETABLERING" -> Etablering
                "UTDANNING" -> Utdanning
                "GJENOPPTAK" -> Gjenopptak
                "KLAGE_ANKE" -> KlageAnke
                else -> throw UgyldigHenvendelseException(henvendelsesnavn)
            }
        }
    }
}

object NyttSaksforhold : Henvendelse("Start Vedtaksbehandling - automatisk journalført.\n")

sealed class EksisterendeSaksforhold(oppgavebeskrivelse: String) : Henvendelse(oppgavebeskrivelse)

object Etablering : EksisterendeSaksforhold("Etablering\n")
object Utdanning : EksisterendeSaksforhold("Utdanning\n")
object Gjenopptak : EksisterendeSaksforhold("Gjenopptak\n")
object KlageAnke : EksisterendeSaksforhold("Klage og anke\n")

class UgyldigHenvendelseException(val henvendelsesnavn: String) : RuntimeException()