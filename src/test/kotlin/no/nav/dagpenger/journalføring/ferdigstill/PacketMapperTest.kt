package no.nav.dagpenger.journalføring.ferdigstill

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.PacketMapper.dokumentJsonAdapter
import no.nav.dagpenger.journalføring.ferdigstill.adapter.Dokument
import org.junit.jupiter.api.Test

internal class PacketMapperTest {

    @Test
    fun `Finn riktig oppgave beskrivelse når søker er grensearbeider `() {

        mockkStatic("no.nav.dagpenger.journalføring.ferdigstill.AvsluttendeArbeidsforholdKt") {
            val packet = mockk<Packet>(relaxed = true).also {
                every { it.erGrenseArbeider() } returns true
                every { it.harAvsluttetArbeidsforholdFraKonkurs() } returns true
                every { it.getNullableBoolean(PacketKeys.OPPFYLLER_MINSTEINNTEKT) } returns false
                every { it.getNullableBoolean(PacketKeys.KORONAREGELVERK_MINSTEINNTEKT_BRUKT) } returns false
            }
            val benk = PacketMapper.oppgaveBeskrivelseOgBenk(packet)
            benk.beskrivelse shouldBe "SAMMENLEGGINGSSAKER\n"
            benk.id shouldBe "4470"
        }
    }

    @Test
    fun `Finn riktig oppgave beskrivelse ved Konkurs `() {

        mockkStatic("no.nav.dagpenger.journalføring.ferdigstill.AvsluttendeArbeidsforholdKt") {
            val packet = mockk<Packet>(relaxed = true).also {
                every { it.erGrenseArbeider() } returns false
                every { it.harAvsluttetArbeidsforholdFraKonkurs() } returns true
                every { it.getNullableBoolean(PacketKeys.OPPFYLLER_MINSTEINNTEKT) } returns false
                every { it.getNullableBoolean(PacketKeys.KORONAREGELVERK_MINSTEINNTEKT_BRUKT) } returns false
            }
            val benk = PacketMapper.oppgaveBeskrivelseOgBenk(packet)
            benk.beskrivelse shouldBe "Konkurs\n"
            benk.id shouldBe "4450"
        }
    }

    @Test
    fun `Finn riktig oppgave beskrivelse og benk ved oppfyller minsteinntekt ved ordninær   `() {
        mockkStatic("no.nav.dagpenger.journalføring.ferdigstill.AvsluttendeArbeidsforholdKt") {
            val packet = mockk<Packet>(relaxed = true).also {
                every { it.erGrenseArbeider() } returns false
                every { it.harAvsluttetArbeidsforholdFraKonkurs() } returns false
                every { it.getNullableBoolean(PacketKeys.OPPFYLLER_MINSTEINNTEKT) } returns false
                every { it.getNullableBoolean(PacketKeys.KORONAREGELVERK_MINSTEINNTEKT_BRUKT) } returns false
                every { it.getStringValue(PacketKeys.BEHANDLENDE_ENHET) } returns "4450"
            }
            val benk = PacketMapper.oppgaveBeskrivelseOgBenk(packet)
            benk.beskrivelse shouldBe "Minsteinntekt - mulig avslag\n"
            benk.id shouldBe "4451"
        }
    }

    @Test
    fun `Finn riktig oppgave beskrivelse og benk ved oppfyller minsteinntekt ved permittering   `() {
        mockkStatic("no.nav.dagpenger.journalføring.ferdigstill.AvsluttendeArbeidsforholdKt") {
            val packet = mockk<Packet>(relaxed = true).also {
                every { it.erGrenseArbeider() } returns false
                every { it.harAvsluttetArbeidsforholdFraKonkurs() } returns false
                every { it.getNullableBoolean(PacketKeys.OPPFYLLER_MINSTEINNTEKT) } returns false
                every { it.getNullableBoolean(PacketKeys.KORONAREGELVERK_MINSTEINNTEKT_BRUKT) } returns false
                every { it.getStringValue(PacketKeys.BEHANDLENDE_ENHET) } returns "4455"
            }
            val benk = PacketMapper.oppgaveBeskrivelseOgBenk(packet)
            benk.beskrivelse shouldBe "Minsteinntekt - mulig avslag\n"
            benk.id shouldBe "4456"
        }
    }

    @Test
    fun `Finn riktig oppgave beskrivelse og benk ved oppfyller minsteinntekt ved korona regler   `() {

        mockkStatic("no.nav.dagpenger.journalføring.ferdigstill.AvsluttendeArbeidsforholdKt") {
            val packet = mockk<Packet>(relaxed = true).also {
                every { it.erGrenseArbeider() } returns false
                every { it.harAvsluttetArbeidsforholdFraKonkurs() } returns false
                every { it.getNullableBoolean(PacketKeys.OPPFYLLER_MINSTEINNTEKT) } returns false
                every { it.getNullableBoolean(PacketKeys.KORONAREGELVERK_MINSTEINNTEKT_BRUKT) } returns true
                every { it.getStringValue(PacketKeys.BEHANDLENDE_ENHET) } returns "4450"
            }
            val benk = PacketMapper.oppgaveBeskrivelseOgBenk(packet)
            benk.beskrivelse shouldBe "Minsteinntekt - mulig avslag - korona\n"
            benk.id shouldBe "4451"
        }
    }

    @Test
    fun ` Finn riktig oppgavebeskrivelse ved ny søknad `() {
        mockkStatic("no.nav.dagpenger.journalføring.ferdigstill.AvsluttendeArbeidsforholdKt") {
            val packet = mockk<Packet>(relaxed = true).also {
                every { it.erGrenseArbeider() } returns false
                every { it.harAvsluttetArbeidsforholdFraKonkurs() } returns false
                every { it.getNullableBoolean(PacketKeys.OPPFYLLER_MINSTEINNTEKT) } returns true
                every { it.getNullableBoolean(PacketKeys.KORONAREGELVERK_MINSTEINNTEKT_BRUKT) } returns false
                every { it.getStringValue(PacketKeys.HENVENDELSESTYPE) } returns "NY_SØKNAD"
                every { it.getStringValue(PacketKeys.BEHANDLENDE_ENHET) } returns "4450"
            }
            val benk = PacketMapper.oppgaveBeskrivelseOgBenk(packet)
            benk.beskrivelse shouldBe "Start Vedtaksbehandling - automatisk journalført.\n"
            benk.id shouldBe "4450"
        }
    }

    @Test
    fun `Exctract journal post id from packet`() {
        PacketMapper.journalpostIdFrom(
            Packet().apply {
                this.putValue(PacketKeys.JOURNALPOST_ID, "journalPostId")
            }
        ) shouldBe "journalPostId"
    }

    @Test
    fun `Exctract avsender from packet`() {
        PacketMapper.avsenderFrom(
            Packet().apply {
                this.putValue(PacketKeys.AVSENDER_NAVN, "et navn")
            }
        ).navn shouldBe "et navn"
    }

    @Test
    fun `Exctract bruker from packet`() {
        PacketMapper.bruker(
            Packet().apply {
                this.putValue(PacketKeys.NATURLIG_IDENT, "fnr")
            }
        ).id shouldBe "fnr"
    }

    @Test
    fun `Extract dokumenter from packet`() {
        val dokumenter = PacketMapper.dokumenterFrom(
            Packet().apply {
                dokumentJsonAdapter.toJsonValue(
                    listOf(
                        Dokument(
                            "id1",
                            "tittel1"
                        ),
                        Dokument("id2", "tittel2")
                    )
                )?.let {
                    this.putValue(PacketKeys.DOKUMENTER, it)
                }
            }
        )

        dokumenter.size shouldBe 2

        with(dokumenter.first()) {
            this.dokumentInfoId shouldBe "id1"
            this.tittel shouldBe "tittel1"
        }
        with(dokumenter.last()) {
            this.dokumentInfoId shouldBe "id2"
            this.tittel shouldBe "tittel2"
        }
    }

    @Test
    fun `Extract journal post from packet`() {
        val jp = PacketMapper.journalPostFrom(
            Packet().apply {
                this.putValue(PacketKeys.JOURNALPOST_ID, "journalPostId")
                this.putValue(PacketKeys.AVSENDER_NAVN, "navn")
                this.putValue(PacketKeys.NATURLIG_IDENT, "fnr")
                dokumentJsonAdapter.toJsonValue(
                    listOf(
                        Dokument(
                            "dokumentId",
                            "tittel"
                        )
                    )
                )
                    ?.let { this.putValue(PacketKeys.DOKUMENTER, it) }
            },
            FagsakId("bla")
        )

        jp.avsenderMottaker.navn shouldBe "navn"
        jp.behandlingstema shouldBe "ab0001"
        jp.bruker.id shouldBe "fnr"
        jp.bruker.idType shouldBe "FNR"
        jp.dokumenter shouldBe listOf(
            Dokument(
                "dokumentId",
                "tittel"
            )
        )
        jp.journalfoerendeEnhet shouldBe "9999"
        jp.tema shouldBe "DAG"
        jp.tittel shouldBe "tittel"
    }
}
