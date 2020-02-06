package no.nav.dagpenger.journalføring.ferdigstill

sealed class Henvendelse {
    object OmNySaksrelasjon : Henvendelse()
    sealed class OmEksisterendeSaksrelasjon : Henvendelse() {
        object Etablering : OmEksisterendeSaksrelasjon()
        object Utdanning : OmEksisterendeSaksrelasjon()
        object Gjenopptak : OmEksisterendeSaksrelasjon()
        object KlageAnke : OmEksisterendeSaksrelasjon()
    }

    companion object {
        fun fra(henvendelsesnavn: String): Henvendelse {
            return when (henvendelsesnavn) {
                "NY_SØKNAD" -> OmNySaksrelasjon
                "ETABLERING" -> OmEksisterendeSaksrelasjon.Etablering
                "UTDANNING" -> OmEksisterendeSaksrelasjon.Utdanning
                "GJENOPPTAK" -> OmEksisterendeSaksrelasjon.Gjenopptak
                "KLAGE_ANKE" -> OmEksisterendeSaksrelasjon.KlageAnke
                else -> throw UgyldigHenvendelseException(henvendelsesnavn)
            }
        }
    }
}

class UgyldigHenvendelseException(val henvendelsesnavn: String) : RuntimeException()