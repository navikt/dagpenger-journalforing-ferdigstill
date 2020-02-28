package no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.matching.EqualToPattern
import no.nav.dagpenger.events.moshiInstance
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubsumsjonsClientTest {
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
    fun `Should get subsumsjon`() {
        val behovId = "12345"
        val subsumsjonResponse = moshiInstance.adapter(Subsumsjon::class.java).toJson(dummySubsumsjon(behovId))

        val equalToPattern = EqualToPattern("regelApiKey")
        WireMock.stubFor(
            WireMock.get(WireMock.urlEqualTo("//subsumsjon/112233"))
                .withHeader("X-API-KEY", equalToPattern)
                .willReturn(
                    WireMock.aResponse()
                        .withBody(subsumsjonResponse)
                )
        )

        val client = SubsumsjonsClient(server.url(""), equalToPattern.value)

        val subsumsjon = client.getSubsumsjon("/subsumsjon/112233")

        Assertions.assertEquals(behovId, subsumsjon.behovId)
    }
}
