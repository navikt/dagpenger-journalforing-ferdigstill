package no.nav.dagpenger.journalføring.arena.adapter

open class ArenaClientException(e: Exception) : RuntimeException(e)

class BestillOppgaveArenaException(e: Exception) : ArenaClientException(e)
class HentArenaSakerException(e: Exception) : ArenaClientException(e)