package no.nav.dagpenger.journalføring.ferdigstill.adapter.soap.arena

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.journalføring.ferdigstill.FagsakId
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaClient
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaSak
import no.nav.dagpenger.journalføring.ferdigstill.adapter.ArenaSakStatus
import no.nav.dagpenger.journalføring.ferdigstill.adapter.BestillOppgaveArenaException
import no.nav.dagpenger.journalføring.ferdigstill.adapter.HentArenaSakerException
import no.nav.dagpenger.journalføring.ferdigstill.adapter.OppgaveCommand
import no.nav.dagpenger.journalføring.ferdigstill.adapter.StartVedtakCommand
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
import no.nav.tjeneste.virksomhet.behandlearbeidogaktivitetoppgave.v1.meldinger.WSBestillOppgaveResponse
import no.nav.tjeneste.virksomhet.ytelseskontrakt.v3.YtelseskontraktV3
import no.nav.tjeneste.virksomhet.ytelseskontrakt.v3.meldinger.WSHentYtelseskontraktListeRequest
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.GregorianCalendar
import javax.xml.datatype.DatatypeFactory

class SoapArenaClient(private val oppgaveV1: BehandleArbeidOgAktivitetOppgaveV1, private val ytelseskontraktV3: YtelseskontraktV3) :
    ArenaClient {

    override fun bestillOppgave(command: OppgaveCommand): FagsakId? {

        val soapRequest = command.toWSBestillOppgaveRequest()

        val response: WSBestillOppgaveResponse = try {
            retry { oppgaveV1.bestillOppgave(soapRequest) }
        } catch (e: Exception) {
            throw BestillOppgaveArenaException(e)
            // @todo Håndtere BestillOppgaveSikkerhetsbegrensning, BestillOppgaveOrganisasjonIkkeFunnet, BestillOppgavePersonErInaktiv, BestillOppgaveSakIkkeOpprettet, BestillOppgavePersonIkkeFunnet, BestillOppgaveUgyldigInput
        }

        return response.arenaSakId?.let { FagsakId(it) }
    }

    fun OppgaveCommand.toWSBestillOppgaveRequest(): WSBestillOppgaveRequest {
        val soapRequest = WSBestillOppgaveRequest()
        val today = ZonedDateTime.now().toInstant().atZone(ZoneId.of("Europe/Oslo"))

        soapRequest.oppgave = when (this) {
            is StartVedtakCommand -> {
                soapRequest.oppgavetype = WSOppgavetype().apply { value = "STARTVEDTAK" }
                WSOppgave().apply {
                    sakInfo = WSSakInfo().withTvingNySak(true)
                    beskrivelse = "Start Vedtaksbehandling - automatisk journalført.\n"
                }
            }
            is VurderHenvendelseAngåendeEksisterendeSaksforholdCommand -> {
                soapRequest.oppgavetype = WSOppgavetype().apply { value = "BEHENVPERSON" }
                WSOppgave().apply {
                    sakInfo = WSSakInfo().withTvingNySak(false)
                    beskrivelse = oppgavebeskrivelse
                }
            }
        }

        soapRequest.oppgave.apply {
            tema = WSTema().apply { value = "DAG" }
            bruker = WSPerson().apply { ident = naturligIdent }
            behandlendeEnhetId = this@toWSBestillOppgaveRequest.behandlendeEnhetId
            prioritet = WSPrioritet().apply {
                this.value = "HOY"
            }
            frist = DatatypeFactory.newInstance().newXMLGregorianCalendar(GregorianCalendar.from(today))
            this.tilleggsinformasjon = this@toWSBestillOppgaveRequest.tilleggsinformasjon
        }

        return soapRequest
    }

    override fun hentArenaSaker(naturligIdent: String): List<ArenaSak> {
        val request =
            WSHentYtelseskontraktListeRequest()
                .withPersonidentifikator(naturligIdent)

        try {
            val response = ytelseskontraktV3.hentYtelseskontraktListe(request)
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
            ytelseskontraktV3.ping()
            HealthStatus.UP
        } catch (e: Exception) {
            HealthStatus.DOWN
        }
    }

    private fun <T> retry(
        times: Int = 3,
        initialDelay: Long = 1000, // 1 second
        maxDelay: Long = 30000, // 30 second
        factor: Double = 2.0,
        block: () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(times - 1) {
            try {
                return block()
            } catch (e: Exception) {
                if (e is BestillOppgavePersonErInaktiv || e is BestillOppgavePersonIkkeFunnet) throw e
            }
            runBlocking { delay(currentDelay) }
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
        }
        return block() // last attempt
    }
}