package no.nav.dagpenger.journalføring.ferdigstill.adapter.soap.arena

import io.github.resilience4j.core.IntervalFunction
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import mu.KotlinLogging
import no.nav.dagpenger.journalføring.ferdigstill.AdapterException
import no.nav.dagpenger.journalføring.ferdigstill.ArenaIdParRespons
import no.nav.dagpenger.journalføring.ferdigstill.FagsakId
import no.nav.dagpenger.journalføring.ferdigstill.Metrics
import no.nav.dagpenger.journalføring.ferdigstill.OppgaveId
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaSak
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaSakStatus
import no.nav.dagpenger.journalføring.ferdigstill.adapter.Bruker
import no.nav.dagpenger.journalføring.ferdigstill.adapter.HentArenaSakerException
import no.nav.dagpenger.journalføring.ferdigstill.adapter.OppgaveCommand
import no.nav.dagpenger.journalføring.ferdigstill.adapter.StartVedtakCommand
import no.nav.dagpenger.journalføring.ferdigstill.adapter.VurderFornyetRettighetCommand
import no.nav.dagpenger.journalføring.ferdigstill.adapter.VurderHenvendelseAngåendeEksisterendeSaksforholdCommand
import no.nav.dagpenger.streams.HealthStatus
import no.nav.tjeneste.virksomhet.behandlearbeidogaktivitetoppgave.v1.BehandleArbeidOgAktivitetOppgaveV1
import no.nav.tjeneste.virksomhet.behandlearbeidogaktivitetoppgave.v1.BestillOppgavePersonErInaktiv
import no.nav.tjeneste.virksomhet.behandlearbeidogaktivitetoppgave.v1.BestillOppgavePersonIkkeFunnet
import no.nav.tjeneste.virksomhet.behandlearbeidogaktivitetoppgave.v1.informasjon.WSOppgave
import no.nav.tjeneste.virksomhet.behandlearbeidogaktivitetoppgave.v1.informasjon.WSOppgavetype
import no.nav.tjeneste.virksomhet.behandlearbeidogaktivitetoppgave.v1.informasjon.WSPerson
import no.nav.tjeneste.virksomhet.behandlearbeidogaktivitetoppgave.v1.informasjon.WSPrioritet
import no.nav.tjeneste.virksomhet.behandlearbeidogaktivitetoppgave.v1.informasjon.WSSakInfo
import no.nav.tjeneste.virksomhet.behandlearbeidogaktivitetoppgave.v1.informasjon.WSTema
import no.nav.tjeneste.virksomhet.behandlearbeidogaktivitetoppgave.v1.meldinger.WSBestillOppgaveRequest
import no.nav.tjeneste.virksomhet.ytelseskontrakt.v3.YtelseskontraktV3
import no.nav.tjeneste.virksomhet.ytelseskontrakt.v3.meldinger.WSHentYtelseskontraktListeRequest
import java.time.Duration
import java.util.GregorianCalendar
import javax.xml.datatype.DatatypeFactory

class SoapArenaClient(
    private val oppgaveV1: BehandleArbeidOgAktivitetOppgaveV1,
    private val ytelseskontraktV3: YtelseskontraktV3
) :
    ArenaClient {

    private val intervalWithCustomExponentialBackoff: IntervalFunction = IntervalFunction
        .ofExponentialBackoff(IntervalFunction.DEFAULT_INITIAL_INTERVAL, 2.0)

    private val config: RetryConfig = RetryConfig.custom<Any>()
        .maxAttempts(3)
        .waitDuration(Duration.ofMillis(1000))
        .intervalFunction(intervalWithCustomExponentialBackoff)
        .ignoreExceptions(BestillOppgavePersonErInaktiv::class.java, BestillOppgavePersonIkkeFunnet::class.java)
        .build()

    // Create a RetryRegistry with a custom global configuration
    private val registry: RetryRegistry = RetryRegistry.of(config)

    private val bestillOppgaveRetry: Retry = registry.retry("SoapArenaClient.bestillOppgave")
    private val hentArenaSakerRetry: Retry = registry.retry("SoapArenaClient.hentArenaSaker")

    private val logger = KotlinLogging.logger {}

    override fun bestillOppgave(command: OppgaveCommand): ArenaIdParRespons? {

        val soapRequest = command.toWSBestillOppgaveRequest()

        val response = try {
            bestillOppgaveRetry.executeCallable {
                oppgaveV1.bestillOppgave(soapRequest)
            }
        } catch (e: Exception) {
            Metrics.automatiskJournalførtNeiTellerInc(
                reason = e.javaClass.simpleName,
                enhet = command.behandlendeEnhetId
            )
            return when (e) {
                is BestillOppgavePersonErInaktiv -> {
                    logger.warn { "Kan ikke bestille oppgave for journalpost. Person ikke arbeidssøker " }
                    null
                }
                is BestillOppgavePersonIkkeFunnet -> {
                    logger.warn { "Kan ikke bestille oppgave for journalpost. Person ikke funnet i arena " }
                    null
                }
                else -> {
                    logger.warn(e) { "Kan ikke bestille oppgave for journalpost. Ukjent feil. " }
                    throw AdapterException(e)
                }
            }
        }

        Metrics.automatiskJournalførtJaTellerInc(enhet = command.behandlendeEnhetId)

        val oppgaveId = OppgaveId(response.oppgaveId)
        val fagsakId = response.arenaSakId?.let { FagsakId(it) }

        return ArenaIdParRespons(oppgaveId, fagsakId)
    }

    fun OppgaveCommand.toWSBestillOppgaveRequest(): WSBestillOppgaveRequest {
        val soapRequest = WSBestillOppgaveRequest()

        soapRequest.oppgave = when (this) {
            is StartVedtakCommand -> {
                soapRequest.oppgavetype = WSOppgavetype().apply { value = "STARTVEDTAK" }
                WSOppgave().apply {
                    sakInfo = WSSakInfo().withTvingNySak(true)
                }
            }
            is VurderHenvendelseAngåendeEksisterendeSaksforholdCommand -> {
                soapRequest.oppgavetype = WSOppgavetype().apply { value = "BEHENVPERSON" }
                WSOppgave().apply {
                    sakInfo = WSSakInfo().withTvingNySak(false)
                }
            }
            is VurderFornyetRettighetCommand -> {
                soapRequest.oppgavetype = WSOppgavetype().apply { value = "BEHENVPERSON" }
                WSOppgave().apply {
                    sakInfo = WSSakInfo().withTvingNySak(false)
                }
            }
        }

        soapRequest.oppgave.apply {
            beskrivelse = oppgavebeskrivelse
            tema = WSTema().apply { value = "DAG" }
            bruker = WSPerson().apply { ident = naturligIdent }
            behandlendeEnhetId = this@toWSBestillOppgaveRequest.behandlendeEnhetId
            prioritet = WSPrioritet().apply {
                this.value = "HOY"
            }
            frist = DatatypeFactory.newInstance().newXMLGregorianCalendar(GregorianCalendar.from(registrertDato))
            this.tilleggsinformasjon = this@toWSBestillOppgaveRequest.tilleggsinformasjon
        }

        return soapRequest
    }

    override fun harIkkeAktivSak(bruker: Bruker): Boolean {
        val saker = hentArenaSaker(bruker.id)
        return saker.none { it.status == ArenaSakStatus.Aktiv }
    }

    private fun hentArenaSaker(naturligIdent: String): List<ArenaSak> {
        val request =
            WSHentYtelseskontraktListeRequest()
                .withPersonidentifikator(naturligIdent)

        try {
            val response = hentArenaSakerRetry.executeCallable { ytelseskontraktV3.hentYtelseskontraktListe(request) }
            return response.ytelseskontraktListe.filter { it.ytelsestype == "Dagpenger" }.map {
                ArenaSak(
                    it.fagsystemSakId,
                    ArenaSakStatus.valueOf(it.status)
                )
            }
        } catch (e: Exception) {
            throw HentArenaSakerException(e)
        }
    }

    override fun status(): HealthStatus {
        return try {
            oppgaveV1.ping()
            HealthStatus.UP
        } catch (e: Exception) {
            HealthStatus.DOWN
        }
    }
}
