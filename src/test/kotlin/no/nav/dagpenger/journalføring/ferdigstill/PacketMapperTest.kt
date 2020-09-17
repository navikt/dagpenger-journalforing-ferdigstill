package no.nav.dagpenger.journalføring.ferdigstill

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.PacketMapper.dokumentJsonAdapter
import no.nav.dagpenger.journalføring.ferdigstill.adapter.Dokument
import org.junit.jupiter.api.Test

internal class PacketMapperTest {

    @Test
    fun `Finn riktig oppgave beskrivelse`() {

        val packet = mockk<Packet>(relaxed = true).also {
            every { it.harAvsluttetArbeidsforholdFraKonkurs() } returns true
            every { it.getNullableBoolean(PacketKeys.OPPFYLLER_MINSTEINNTEKT) } returns true
            every { it.getNullableBoolean(PacketKeys.KORONAREGELVERK_MINSTEINNTEKT_BRUKT) } returns false
        }
        PacketMapper.oppgaveBeskrivelse(packet) shouldBe "Konkurs\n"
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
