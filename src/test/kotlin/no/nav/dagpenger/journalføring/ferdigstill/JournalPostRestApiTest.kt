package no.nav.dagpenger.journalføring.ferdigstill

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.matching.EqualToPattern
import com.gregwoodfill.assert.shouldStrictlyEqualJson
import io.mockk.mockk
import no.nav.dagpenger.oidc.StsOidcClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.LocalDate

internal class JournalPostRestApiTest {

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
                "beskrivelse": "Opprettet av Digitale Dagpenger",
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

        val client: OppgaveClient = GosysOppgaveClient(server.baseUrl(), stsOidcClient)

        assertDoesNotThrow {
            client.opprettOppgave(
                journalPostId = "12345",
                aktørId = "12345678910",
                tildeltEnhetsnr = "9999"
            )
        }
    }
}
