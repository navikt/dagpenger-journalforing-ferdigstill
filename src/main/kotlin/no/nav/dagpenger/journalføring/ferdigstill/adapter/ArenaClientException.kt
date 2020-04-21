package no.nav.dagpenger.journalføring.ferdigstill.adapter

open class ArenaClientException(e: Exception) : RuntimeException(e)

class HentArenaSakerException(e: Exception) : ArenaClientException(e)
