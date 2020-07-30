package no.nav.dagpenger.journalføring.ferdigstill.adapter

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import io.mockk.mockk
import no.nav.dagpenger.journalføring.ferdigstill.AdapterException
import no.nav.dagpenger.oidc.StsOidcClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class JournalpostRestApiTest {

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
    fun `Håndtere 400 feil i oppdatering journalpost`() {

        val journalpostId = "12345"

        WireMock.stubFor(
            WireMock.put(WireMock.urlEqualTo("/rest/journalpostapi/v1/journalpost/$journalpostId"))
                .willReturn(
                    WireMock.aResponse().withStatus(400).withBody("<html><body><h1>Whitelabel Error Page</h1><p>This application has no explicit mapping for /error, so you are seeing this as a fallback.</p><div id='created'>Mon Mar 16 14:01:35 CET 2020</div><div>There was an unexpected error (type=Bad Request, status=400).</div><div>Bruker kan ikke oppdateres for journalpost med journalpostStatus=J og journalpostType=I.</div></body></html>")
                )
        )

        val stsOidcClient: StsOidcClient = mockk(relaxed = true)

        val client = JournalpostRestApi(
            server.baseUrl(),
            stsOidcClient
        )

        client.oppdater(
            journalpostId,
            OppdaterJournalpostPayload(
                avsenderMottaker = Avsender("navn"),
                bruker = Bruker("bruker"),
                tittel = "tittel",
                sak = Sak(
                    SaksType.FAGSAK,
                    "fagsakId",
                    "AO01"
                ),
                dokumenter = listOf(
                    Dokument(
                        "dokumentId",
                        "tittel"
                    )
                )
            )
        )
    }

    @Test
    fun `Håndtere 400 feil i oppdatering journalpost der response er escapet `() {

        val journalpostId = "12345"

        WireMock.stubFor(
            WireMock.put(WireMock.urlEqualTo("/rest/journalpostapi/v1/journalpost/$journalpostId"))
                .willReturn(
                    WireMock.aResponse().withStatus(400).withBody("<html><body><h1>Whitelabel Error Page</h1><p>This application has no explicit mapping for /error, so you are seeing this as a fallback.</p><div id='created'>Mon Mar 16 14:01:35 CET 2020</div><div>There was an unexpected error (type=Bad Request, status=400).</div><div>Bruker kan ikke oppdateres for journalpost med journalpostStatus=J og journalpostType=I.</div></body></html>")
                )
        )

        val stsOidcClient: StsOidcClient = mockk(relaxed = true)

        val client = JournalpostRestApi(
            server.baseUrl(),
            stsOidcClient
        )

        client.oppdater(
            journalpostId,
            OppdaterJournalpostPayload(
                avsenderMottaker = Avsender("navn"),
                bruker = Bruker("bruker"),
                tittel = "tittel",
                sak = Sak(
                    SaksType.FAGSAK,
                    "fagsakId",
                    "AO01"
                ),
                dokumenter = listOf(
                    Dokument(
                        "dokumentId",
                        "tittel"
                    )
                )
            )
        )
    }

    @Test
    fun `skal håndtere hvis journalpost allerede er ferdigstilt`() {

        val journalpostId = "12345"
        WireMock.stubFor(
            WireMock.patch(WireMock.urlEqualTo("/rest/journalpostapi/v1/journalpost/$journalpostId/ferdigstill"))
                .willReturn(
                    WireMock.aResponse().withStatus(400).withBody("<html><body><h1>Whitelabel Error Page</h1><p>This application has no explicit mapping for /error, so you are seeing this as a fallback.</p><div id='created'>Mon Mar 16 14:55:34 CET 2020</div><div>There was an unexpected error (type=Bad Request, status=400).</div><div>Journalpost med journalpostId=12345 er ikke midlertidig journalf&oslash;rt</div></body></html>")
                )
        )

        val stsOidcClient: StsOidcClient = mockk(relaxed = true)

        val client = JournalpostRestApi(
            server.baseUrl(),
            stsOidcClient
        )

        client.ferdigstill(journalpostId)
    }

    @Test
    fun `Forsøker på ny hvis noe er feil`() {
        val journalpostId = "12345"

        WireMock.stubFor(
            WireMock.put(WireMock.urlEqualTo("/rest/journalpostapi/v1/journalpost/$journalpostId"))
                .willReturn(
                    WireMock.aResponse().withStatus(500)
                )
        )

        WireMock.stubFor(
            WireMock.patch(WireMock.urlEqualTo("/rest/journalpostapi/v1/journalpost/$journalpostId/ferdigstill"))
                .willReturn(
                    WireMock.aResponse().withStatus(500)
                )
        )

        val stsOidcClient: StsOidcClient = mockk(relaxed = true)

        val client = JournalpostRestApi(
            server.baseUrl(),
            stsOidcClient
        )

        assertFailsWith<AdapterException> {
            client.oppdater(
                journalpostId,
                OppdaterJournalpostPayload(
                    avsenderMottaker = Avsender("navn"),
                    bruker = Bruker("bruker"),
                    tittel = "tittel",
                    sak = Sak(
                        SaksType.FAGSAK,
                        "fagsakId",
                        "AO01"
                    ),
                    dokumenter = listOf(
                        Dokument(
                            "dokumentId",
                            "tittel"
                        )
                    )
                )
            )
        }

        WireMock.verify(
            3,
            WireMock.putRequestedFor(WireMock.urlEqualTo("/rest/journalpostapi/v1/journalpost/$journalpostId"))
        )

        assertFailsWith<AdapterException> {
            client.ferdigstill(journalpostId)
        }

        WireMock.verify(
            3,
            WireMock.patchRequestedFor(WireMock.urlEqualTo("/rest/journalpostapi/v1/journalpost/$journalpostId/ferdigstill"))
        )
    }
}
