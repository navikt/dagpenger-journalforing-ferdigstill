package no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.matching.EqualToPattern
import com.github.tomakehurst.wiremock.stubbing.Scenario
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BehovStatusPollerTest {
    companion object {
        val server: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())

        @BeforeAll
        @JvmStatic
        fun start() {
            server.start()
        }

        @AfterAll
        @JvmStatic
        fun stop() {
            server.stop()
        }
    }

    @BeforeEach
    fun configure() {
        WireMock.configureFor(server.port())
    }

    @Test
    fun ` Poller for status frem til den får redirect `() {
        val pattern = EqualToPattern("regelApiKey")

        WireMock.stubFor(
            WireMock.get(WireMock.urlEqualTo("//behov/status/123"))
                .withHeader("X-API-KEY", pattern)
                .inScenario("Retry Scenario")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(
                    WireMock.aResponse()
                        .withBody(responseBody)
                )
                .willSetStateTo("First pending")
        )

        WireMock.stubFor(
            WireMock.get(WireMock.urlEqualTo("//behov/status/123"))
                .withHeader("X-API-KEY", pattern)
                .inScenario("Retry Scenario")
                .whenScenarioStateIs("First pending")
                .willReturn(
                    WireMock.aResponse()
                        .withBody(responseBody)
                )
                .willSetStateTo("Second pending")
        )

        WireMock.stubFor(
            WireMock.get(WireMock.urlEqualTo("//behov/status/123"))
                .withHeader("X-API-KEY", pattern)
                .inScenario("Retry Scenario")
                .whenScenarioStateIs("Second pending")
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(303)
                        .withHeader("Location", "54321")
                )
        )

        val client =
            BehovStatusPoller(regelApiUrl = server.url(""), regelApiKey = pattern.value)

        val response = runBlocking { client.pollStatus("/behov/status/123") }
        Assertions.assertEquals("54321", response)
    }

    val responseBody = """
                {
                        "status" : "PENDING"
                }
            """.trimIndent()
}