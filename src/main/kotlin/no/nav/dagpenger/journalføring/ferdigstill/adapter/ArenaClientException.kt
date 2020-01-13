package no.nav.dagpenger.journalføring.ferdigstill.adapter

open class ArenaClientException(e: Exception) : RuntimeException(e)

class BestillOppgaveArenaException(e: Exception) : ArenaClientException(e)
class HentArenaSakerException(e: Exception) : ArenaClientException(e)