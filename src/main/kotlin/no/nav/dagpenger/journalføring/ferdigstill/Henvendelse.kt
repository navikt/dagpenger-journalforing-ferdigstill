package no.nav.dagpenger.journalføring.ferdigstill

import no.nav.dagpenger.events.Packet

sealed class Henvendelse {
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

object NyttSaksforhold : Henvendelse()

sealed class EksisterendeSaksforhold(val oppgavebeskrivelse: String) : Henvendelse()

object Etablering : EksisterendeSaksforhold("Etablering\n")
object Utdanning : EksisterendeSaksforhold("Utdanning\n")
object Gjenopptak : EksisterendeSaksforhold("Gjenopptak\n")
object KlageAnke : EksisterendeSaksforhold("Klage og anke\n")

class UgyldigHenvendelseException(val henvendelsesnavn: String) : RuntimeException()