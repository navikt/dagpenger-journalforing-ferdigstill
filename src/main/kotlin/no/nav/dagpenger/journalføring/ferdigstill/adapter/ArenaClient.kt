package no.nav.dagpenger.journalføring.ferdigstill.adapter

import mu.KotlinLogging
import no.nav.dagpenger.journalføring.ferdigstill.AdapterException
import no.nav.dagpenger.journalføring.ferdigstill.FagsakId
import no.nav.dagpenger.journalføring.ferdigstill.Metrics
import no.nav.dagpenger.streams.HealthCheck
import no.nav.tjeneste.virksomhet.behandlearbeidogaktivitetoppgave.v1.BestillOppgavePersonErInaktiv
import no.nav.tjeneste.virksomhet.behandlearbeidogaktivitetoppgave.v1.BestillOppgavePersonIkkeFunnet
import java.time.ZonedDateTime


private val logger = KotlinLogging.logger {}


interface ArenaClient : HealthCheck {
    fun bestillOppgave(command: OppgaveCommand): FagsakId?
    fun bestillOppgave(command: OppgaveCommand, journalpostId: String): FagsakId? {
        return try {
            bestillOppgave(command)
        } catch (e: BestillOppgaveArenaException) {
            Metrics.automatiskJournalførtNeiTellerInc(e.cause?.javaClass?.simpleName ?: "ukjent")

            return when (e.cause) {
                is BestillOppgavePersonErInaktiv -> {
                    logger.warn { "Kan ikke bestille oppgave for journalpost $journalpostId. Person ikke arbeidssøker " }
                    null
                }
                is BestillOppgavePersonIkkeFunnet -> {
                    logger.warn { "Kan ikke bestille oppgave for journalpost $journalpostId. Person ikke funnet i arena " }
                    null
                }
                else -> {
                    logger.warn { "Kan ikke bestille oppgave for journalpost $journalpostId. Ukjent feil. " }
                    throw AdapterException(e)
                }
            }
        }
    }

    fun harIkkeAktivSak(bruker: Bruker): Boolean {
        val saker = hentArenaSaker(bruker.id)
        return saker.none { it.status == ArenaSakStatus.Aktiv }
            .also { if (!it) Metrics.automatiskJournalførtNeiTellerInc("aktiv_sak") }
    }

    fun hentArenaSaker(naturligIdent: String): List<ArenaSak>
}

sealed class OppgaveCommand {
    abstract val naturligIdent: String
    abstract val behandlendeEnhetId: String
    abstract val tilleggsinformasjon: String
    abstract val registrertDato: ZonedDateTime
}

class StartVedtakCommand(
    override val naturligIdent: String,
    override val behandlendeEnhetId: String,
    override val tilleggsinformasjon: String,
    override val registrertDato: ZonedDateTime
) : OppgaveCommand()

class VurderHenvendelseAngåendeEksisterendeSaksforholdCommand(
    override val naturligIdent: String,
    override val behandlendeEnhetId: String,
    override val tilleggsinformasjon: String,
    override val registrertDato: ZonedDateTime,
    val oppgavebeskrivelse: String
) : OppgaveCommand()
