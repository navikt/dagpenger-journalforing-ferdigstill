package no.nav.dagpenger.journalføring.ferdigstill.adapter.soap.arena

import com.github.kittinunf.result.Result
import io.kotlintest.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.journalføring.ferdigstill.AdapterException
import no.nav.dagpenger.journalføring.ferdigstill.FagsakId
import no.nav.dagpenger.journalføring.ferdigstill.adapter.Bruker
import no.nav.dagpenger.journalføring.ferdigstill.adapter.StartVedtakCommand
import no.nav.dagpenger.journalføring.ferdigstill.adapter.VurderHenvendelseAngåendeEksisterendeSaksforholdCommand
import no.nav.dagpenger.streams.HealthStatus
import no.nav.tjeneste.virksomhet.behandlearbeidogaktivitetoppgave.v1.BehandleArbeidOgAktivitetOppgaveV1
import no.nav.tjeneste.virksomhet.behandlearbeidogaktivitetoppgave.v1.BestillOppgaveSakIkkeOpprettet
import no.nav.tjeneste.virksomhet.behandlearbeidogaktivitetoppgave.v1.meldinger.WSBestillOppgaveResponse
import no.nav.tjeneste.virksomhet.ytelseskontrakt.v3.YtelseskontraktV3
import no.nav.tjeneste.virksomhet.ytelseskontrakt.v3.informasjon.ytelseskontrakt.WSDagpengekontrakt
import no.nav.tjeneste.virksomhet.ytelseskontrakt.v3.meldinger.WSHentYtelseskontraktListeResponse
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import kotlin.test.assertFailsWith

internal class SoapArenaClientTest {

    @Test
    fun `suksessfull bestilling av vedtaksbehandling gir arenaSakId `() {
        val stubbedClient = mockk<BehandleArbeidOgAktivitetOppgaveV1>()
        every { stubbedClient.bestillOppgave(any()) } returns WSBestillOppgaveResponse().withArenaSakId("123")

        val client = SoapArenaClient(stubbedClient, mockk())

        val actual = client.bestillOppgave(StartVedtakCommand("123", "abc", "", ZonedDateTime.now(), ""))

        actual shouldBe Result.success(FagsakId("123"))
    }

    @Test
    fun `Skal kunne hente arena saker basert på bruker id`() {

        val ytelseskontraktV3: YtelseskontraktV3 = mockk()

        every {
            ytelseskontraktV3.hentYtelseskontraktListe(any())
        } returns WSHentYtelseskontraktListeResponse().withYtelseskontraktListe(
            listOf(
                WSDagpengekontrakt().withFagsystemSakId(
                    123
                ).withStatus("Inaktiv").withYtelsestype("Dagpenger")
            )
        )

        val client = SoapArenaClient(mockk(), ytelseskontraktV3)
        val harIkkeAktivSak = client.harIkkeAktivSak(Bruker("123"))

        harIkkeAktivSak shouldBe true
    }

    @Test
    fun `bestillOppgave skal kunne bestille oppgave uten saksreferanse`() {
        val stubbedClient = mockk<BehandleArbeidOgAktivitetOppgaveV1>()
        every { stubbedClient.bestillOppgave(any()) } returns WSBestillOppgaveResponse()

        val client = SoapArenaClient(stubbedClient, mockk())

        client.bestillOppgave(
            VurderHenvendelseAngåendeEksisterendeSaksforholdCommand(
                "123456789",
                "abcbscb",
                "beskrivelse",
                ZonedDateTime.now(),
                ""
            )
        )
    }

    @Test
    fun `prøver på ny hvis det skjer en ukjent feil`() {
        val stubbedClient = mockk<BehandleArbeidOgAktivitetOppgaveV1>()
        every { stubbedClient.bestillOppgave(any()) } throws BestillOppgaveSakIkkeOpprettet()

        val client = SoapArenaClient(stubbedClient, mockk())

        assertFailsWith<AdapterException> {
            client.bestillOppgave(StartVedtakCommand("123456789", "abcbscb", "beskrivelse", ZonedDateTime.now(), ""))
        }

        verify(exactly = 3) { stubbedClient.bestillOppgave(any()) }
    }

    @Test
    fun ` helsesjekk skal være ok når Arena er oppe`() {
        val behandleArbeidOgAktivitetOppgaveV1: BehandleArbeidOgAktivitetOppgaveV1 = mockk()
        every {
            behandleArbeidOgAktivitetOppgaveV1.ping()
        } returns Unit

        val client = SoapArenaClient(behandleArbeidOgAktivitetOppgaveV1, mockk())
        client.status() shouldBe HealthStatus.UP
    }

    @Test
    fun ` helsesjekk skal ikke være ok når Arena nede`() {
        val behandleArbeidOgAktivitetOppgaveV1: BehandleArbeidOgAktivitetOppgaveV1 = mockk()
        every {
            behandleArbeidOgAktivitetOppgaveV1.ping()
        } throws RuntimeException()

        val client = SoapArenaClient(behandleArbeidOgAktivitetOppgaveV1, mockk())
        client.status() shouldBe HealthStatus.DOWN
    }
}