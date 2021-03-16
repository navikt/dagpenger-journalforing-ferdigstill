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
                "ETTERSENDELSE" -> Ettersendelse
                "MANUELL" -> Manuell
                "KLAGE_ANKE_LONNSKOMPENSASJON" -> KlageAnkeLonnskompensasjon
                else -> throw UgyldigHenvendelseException(henvendelsesnavn)
            }
        }
    }
}

object NyttSaksforhold : Henvendelse("Start Vedtaksbehandling - automatisk journalført.\n")
object Manuell : Henvendelse("Manuell.\n")
object KlageAnkeLonnskompensasjon : Henvendelse("Vurder henvendelse")

sealed class EksisterendeSaksforhold(oppgavebeskrivelse: String) : Henvendelse(oppgavebeskrivelse)

object Etablering : EksisterendeSaksforhold("Etablering\n")
object Utdanning : EksisterendeSaksforhold("Utdanning\n")
object Gjenopptak : EksisterendeSaksforhold("Gjenopptak\n")
object KlageAnke : EksisterendeSaksforhold("Klage og anke\n")
object Ettersendelse : EksisterendeSaksforhold("Behandle henvendelse - automatisk journalført.\n")

class UgyldigHenvendelseException(val henvendelsesnavn: String) : RuntimeException()
