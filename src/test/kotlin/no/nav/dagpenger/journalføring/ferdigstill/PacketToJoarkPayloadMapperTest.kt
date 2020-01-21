package no.nav.dagpenger.journalføring.ferdigstill

import io.kotlintest.shouldBe
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.PacketToJoarkPayloadMapper.dokumentJsonAdapter
import org.junit.jupiter.api.Test

internal class PacketToJoarkPayloadMapperTest {

    @Test
    fun `Exctract journal post id from packet`() {
        PacketToJoarkPayloadMapper.journalpostIdFrom(Packet().apply {
            this.putValue(PacketKeys.JOURNALPOST_ID, "journalPostId")
        }) shouldBe "journalPostId"
    }

    @Test
    fun `Exctract avsender from packet`() {
        PacketToJoarkPayloadMapper.avsenderFrom(Packet().apply {
            this.putValue(PacketKeys.AVSENDER_NAVN, "et navn")
        }).navn shouldBe "et navn"
    }

    @Test
    fun `Exctract bruker from packet`() {
        PacketToJoarkPayloadMapper.brukerFrom(Packet().apply {
            this.putValue(PacketKeys.NATURLIG_IDENT, "fnr")
        }).id shouldBe "fnr"
    }

    @Test
    fun `Extract dokumenter from packet`() {
        val dokumenter = PacketToJoarkPayloadMapper.dokumenterFrom(Packet().apply {
            dokumentJsonAdapter.toJsonValue(listOf(Dokument("id1", "tittel1"), Dokument("id2", "tittel2")))?.let {
                this.putValue(PacketKeys.DOKUMENTER, it)
            }
        })

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
        val jp = PacketToJoarkPayloadMapper.journalPostFrom(
            Packet().apply {
                this.putValue(PacketKeys.JOURNALPOST_ID, "journalPostId")
                this.putValue(PacketKeys.AVSENDER_NAVN, "navn")
                this.putValue(PacketKeys.NATURLIG_IDENT, "fnr")
                dokumentJsonAdapter.toJsonValue(listOf(Dokument("dokumentId", "tittel")))
                    ?.let { this.putValue(PacketKeys.DOKUMENTER, it) }
            },
            "bla"
        )

        jp.avsenderMottaker.navn shouldBe "navn"
        jp.behandlingstema shouldBe "ab0001"
        jp.bruker.id shouldBe "fnr"
        jp.bruker.idType shouldBe "FNR"
        jp.dokumenter shouldBe listOf(Dokument("dokumentId", "tittel"))
        jp.journalfoerendeEnhet shouldBe "9999"
        jp.tema shouldBe "DAG"
        jp.tittel shouldBe "tittel"
    }
}
