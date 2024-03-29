package no.nav.dagpenger.journalføring.ferdigstill.adapter

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.httpPost
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import mu.KotlinLogging
import no.nav.dagpenger.events.LocalDateJsonAdapter
import no.nav.dagpenger.journalføring.ferdigstill.AdapterException
import no.nav.dagpenger.oidc.OidcClient
import java.time.LocalDate
import java.time.ZonedDateTime

private val logger = KotlinLogging.logger {}

// DOK https://confluence.adeo.no/display/TO/Systemdokumentasjon+Oppgave

internal interface ManuellJournalføringsOppgaveClient {
    fun opprettOppgave(
        journalPostId: String,
        aktørId: String?,
        søknadstittel: String,
        tildeltEnhetsnr: String,
        frist: ZonedDateTime
    )
}

internal data class GosysOppgave(
    val journalpostId: String,
    val aktoerId: String?,
    val tildeltEnhetsnr: String,
    val beskrivelse: String = "Kunne ikke automatisk journalføres",
    val opprettetAvEnhetsnr: String = "9999",
    val tema: String = "DAG",
    val oppgavetype: String = "JFR",
    val aktivDato: LocalDate,
    val fristFerdigstillelse: LocalDate,
    val prioritet: String = "NORM"
)

internal class GosysOppgaveClient(private val url: String, private val oidcClient: OidcClient) :
    ManuellJournalføringsOppgaveClient {

    companion object {
        private val moishiInstance = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .add(LocalDateJsonAdapter())
            .build()

        private val jacksonMapper = jacksonObjectMapper()

        private val Journalføring = "JFR"

        fun toOpprettGosysOppgaveJsonPayload(gosysOppgave: GosysOppgave) =
            moishiInstance.adapter(
                GosysOppgave::class.java
            ).toJson(gosysOppgave)
    }

    override fun opprettOppgave(
        journalPostId: String,
        aktørId: String?,
        søknadstittel: String,
        tildeltEnhetsnr: String,
        frist: ZonedDateTime
    ) {

        val (_, _, result) = retryFuel(
            initialDelay = 5000,
            maxDelay = 30000
        ) {
            url.plus("/api/v1/oppgaver")
                .httpPost()
                .authentication()
                .bearer(oidcClient.oidcToken().access_token)
                .header("X-Correlation-ID", journalPostId)
                .jsonBody(
                    toOpprettGosysOppgaveJsonPayload(
                        GosysOppgave(
                            journalpostId = journalPostId,
                            aktoerId = aktørId,
                            beskrivelse = søknadstittel,
                            tildeltEnhetsnr = tildeltEnhetsnr,
                            aktivDato = frist.toLocalDate(),
                            fristFerdigstillelse = frist.toLocalDate(),
                            oppgavetype = Journalføring
                        )
                    )
                )
                .response()
        }

        result.fold(
            {
                val gosysoppgave = kotlin.runCatching { jacksonMapper.readTree(it) }.getOrNull()
                val oppgaveId = gosysoppgave?.get("id") ?: "ukjent"
                logger.info(" Opprettet manuell journalføringsoppgave med oppgaveid '$oppgaveId' for $journalPostId")
            },
            { e ->
                logger.error(
                    "Feilet oppretting av manuell journalføringsoppgave for $journalPostId response message: ${e.response.body().asString(
                        "application/json"
                    )}",
                    e.exception
                )
                throw AdapterException(e.exception)
            }
        )
    }
}
