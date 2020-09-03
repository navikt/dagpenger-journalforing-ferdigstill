package no.nav.dagpenger.journalføring.ferdigstill.adapter

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo // ktlint-disable
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.matching.EqualToPattern
import com.gregwoodfill.assert.shouldStrictlyEqualJson
import io.mockk.mockk
import no.nav.dagpenger.journalføring.ferdigstill.AdapterException
import no.nav.dagpenger.oidc.StsOidcClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.test.assertFailsWith

internal class GosysOppgaveClientTest {

    companion object {
        val server: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())

        @BeforeAll
        @JvmStatic
        fun start() {
            server.start()
            WireMock.configureFor(server.port())
        }

        @AfterAll
        @JvmStatic
        fun stop() {
            server.stop()
        }
    }

    @Test
    fun `Opprett gosys-oppgave json payload`() {
        val gosysOppgave = GosysOppgave(
            journalpostId = "12345",
            aktoerId = "12345678910",
            aktivDato = LocalDate.of(2019, 12, 11),
            fristFerdigstillelse = LocalDate.of(2019, 12, 12),
            tildeltEnhetsnr = "9999"

        )

        val json = GosysOppgaveClient.toOpprettGosysOppgaveJsonPayload(gosysOppgave)

        json shouldStrictlyEqualJson """
            {
                "journalpostId": "12345",
                "aktoerId": "12345678910",
                "tildeltEnhetsnr": "4450",
                "opprettetAvEnhetsnr": "9999",
                "beskrivelse": "Kunne ikke automatisk journalføres",
                "tema": "DAG",
                "oppgavetype": "JFR",
                "aktivDato": "2019-12-11",
                "fristFerdigstillelse": "2019-12-12",
                "prioritet": "NORM",
                "tildeltEnhetsnr": "9999"
            }
        """.trimIndent()
    }

    @Test
    fun `Opprett gosys-oppgave contract test`() {
        WireMock.stubFor(
            WireMock.post(WireMock.urlEqualTo("/api/v1/oppgaver"))
                .withHeader("X-Correlation-ID", EqualToPattern("12345"))
                .willReturn(
                    WireMock.aResponse().withStatus(201)
                )
        )

        val stsOidcClient: StsOidcClient = mockk(relaxed = true)

        val client: ManuellJournalføringsOppgaveClient =
            GosysOppgaveClient(
                server.baseUrl(),
                stsOidcClient
            )

        assertDoesNotThrow {
            client.opprettOppgave(
                journalPostId = "12345",
                aktørId = "12345678910",
                søknadstittel = "tittel1",
                tildeltEnhetsnr = "9999",
                frist = ZonedDateTime.now()
            )
        }
    }

    @Test
    fun `Forsøker på ny hvis noe er feil`() {
        WireMock.stubFor(
            WireMock.post(urlEqualTo("/api/v1/oppgaver"))
                .withHeader("X-Correlation-ID", EqualToPattern("12345"))
                .willReturn(
                    WireMock.aResponse().withStatus(500)
                )
        )

        val stsOidcClient: StsOidcClient = mockk(relaxed = true)

        val client: ManuellJournalføringsOppgaveClient =
            GosysOppgaveClient(
                server.baseUrl(),
                stsOidcClient
            )

        assertFailsWith<AdapterException> {
            client.opprettOppgave(
                journalPostId = "12345",
                aktørId = "12345678910",
                søknadstittel = "tittel1",
                tildeltEnhetsnr = "9999",
                frist = ZonedDateTime.now()
            )
        }

        WireMock.verify(3, WireMock.postRequestedFor(urlEqualTo("/api/v1/oppgaver")))
    }
}
