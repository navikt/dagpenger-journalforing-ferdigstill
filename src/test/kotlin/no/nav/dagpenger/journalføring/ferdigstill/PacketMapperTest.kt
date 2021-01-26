package no.nav.dagpenger.journalføring.ferdigstill

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import no.nav.dagpenger.events.Packet
import no.nav.dagpenger.journalføring.ferdigstill.PacketMapper.OppgaveBenk
import no.nav.dagpenger.journalføring.ferdigstill.PacketMapper.dokumentJsonAdapter
import no.nav.dagpenger.journalføring.ferdigstill.adapter.Dokument
import org.junit.jupiter.api.Test

internal class PacketMapperTest {

    private fun beregnOppgaveBenk(
        harEøsArbeidsforhold: Boolean = false,
        harAvtjentVerneplikt: Boolean = false,
        harInntektFraFangstOgFiske: Boolean,
        erGrenseArbeider: Boolean,
        harAvsluttetArbeidsforholdFraKonkurs: Boolean,
        oppfyllerMinsteinntekt: Boolean,
        koronaRegelverkForMinsteinntektBrukt: Boolean,
        behandlendeEnhet: String = "4450",
        henvendelseType: String = "NY_SØKNAD",
        erPermittertFraFiskeforedling: Boolean = true,

    ): OppgaveBenk {
        lateinit var oppgaveBenk: OppgaveBenk
        mockkStatic(
            "no.nav.dagpenger.journalføring.ferdigstill.AvsluttendeArbeidsforholdKt",
            "no.nav.dagpenger.journalføring.ferdigstill.PacketMapperKt"
        ) {
            val packet = mockk<Packet>(relaxed = false).also {
                every { it.harEøsArbeidsforhold() } returns harEøsArbeidsforhold
                every { it.harAvtjentVerneplikt() } returns harAvtjentVerneplikt
                every { it.harInntektFraFangstOgFiske() } returns harInntektFraFangstOgFiske
                every { it.erGrenseArbeider() } returns erGrenseArbeider
                every { it.harAvsluttetArbeidsforholdFraKonkurs() } returns harAvsluttetArbeidsforholdFraKonkurs
                every { it.erPermittertFraFiskeForedling() } returns erPermittertFraFiskeforedling
                every { it.getNullableBoolean(PacketKeys.OPPFYLLER_MINSTEINNTEKT) } returns oppfyllerMinsteinntekt
                every { it.getNullableBoolean(PacketKeys.KORONAREGELVERK_MINSTEINNTEKT_BRUKT) } returns koronaRegelverkForMinsteinntektBrukt
                every { it.getStringValue(PacketKeys.BEHANDLENDE_ENHET) } returns behandlendeEnhet
                every { it.getStringValue(PacketKeys.HENVENDELSESTYPE) } returns henvendelseType
            }
            oppgaveBenk = PacketMapper.oppgaveBeskrivelseOgBenk(packet)
        }
        return oppgaveBenk
    }

    @Test
    fun `Finn riktig oppgave beskrivelse og benk når søker har eøs arbeidsforhold de siste 3 årene `() {
        beregnOppgaveBenk(
            harEøsArbeidsforhold = true,
            harAvtjentVerneplikt = true,
            harInntektFraFangstOgFiske = true,
            erGrenseArbeider = true,
            harAvsluttetArbeidsforholdFraKonkurs = true,
            oppfyllerMinsteinntekt = false,
            koronaRegelverkForMinsteinntektBrukt = false,
            behandlendeEnhet = "4455"
        ) shouldBe OppgaveBenk("4470", "MULIG SAMMENLEGGING - EØS\n")
    }

    @Test
    fun `Bruk original benk når bruker har diskresjonskode`() {
        beregnOppgaveBenk(
            harEøsArbeidsforhold = true,
            harAvtjentVerneplikt = true,
            harInntektFraFangstOgFiske = true,
            erGrenseArbeider = true,
            harAvsluttetArbeidsforholdFraKonkurs = true,
            oppfyllerMinsteinntekt = false,
            koronaRegelverkForMinsteinntektBrukt = false,
            behandlendeEnhet = "2103"
        ) shouldBe OppgaveBenk("2103", "Start Vedtaksbehandling - automatisk journalført.\n")
    }

    @Test
    fun `Finn riktig oppgave beskrivelse og benk når søker har avtjent verneplikt IKKE verneplikt`() {
        beregnOppgaveBenk(
            harEøsArbeidsforhold = false,
            harAvtjentVerneplikt = true,
            harInntektFraFangstOgFiske = true,
            erGrenseArbeider = true,
            harAvsluttetArbeidsforholdFraKonkurs = true,
            oppfyllerMinsteinntekt = false,
            koronaRegelverkForMinsteinntektBrukt = false,
            behandlendeEnhet = "4450"
        ) shouldBe OppgaveBenk("4450", "VERNEPLIKT\n")
    }

    @Test
    fun `Finn riktig oppgave beskrivelse og benk når søker har inntekt fra fangst og fisk ordinær`() {
        beregnOppgaveBenk(
            harEøsArbeidsforhold = false,
            harInntektFraFangstOgFiske = true,
            erGrenseArbeider = true,
            harAvsluttetArbeidsforholdFraKonkurs = true,
            oppfyllerMinsteinntekt = false,
            koronaRegelverkForMinsteinntektBrukt = false,
            behandlendeEnhet = "4455"
        ) shouldBe OppgaveBenk("4455", "FANGST OG FISKE\n")
    }

    @Test
    fun `Finn riktig oppgave beskrivelse og benk når søker har inntekt fra fangst og fisk IKKE permttert`() {
        beregnOppgaveBenk(
            harEøsArbeidsforhold = false,
            harInntektFraFangstOgFiske = true,
            erGrenseArbeider = true,
            harAvsluttetArbeidsforholdFraKonkurs = true,
            oppfyllerMinsteinntekt = false,
            koronaRegelverkForMinsteinntektBrukt = false,
            behandlendeEnhet = "4450"
        ) shouldBe OppgaveBenk("4450", "FANGST OG FISKE\n")
    }

    @Test
    fun `Finn riktig oppgave beskrivelse når søker er grensearbeider `() {
        beregnOppgaveBenk(
            harEøsArbeidsforhold = false,
            harInntektFraFangstOgFiske = false,
            erGrenseArbeider = true,
            harAvsluttetArbeidsforholdFraKonkurs = true,
            oppfyllerMinsteinntekt = false,
            koronaRegelverkForMinsteinntektBrukt = false,
        ) shouldBe OppgaveBenk("4465", "EØS\n")
    }

    @Test
    fun `Finn riktig oppgave beskrivelse ved Konkurs `() {
        beregnOppgaveBenk(
            harEøsArbeidsforhold = false,
            harInntektFraFangstOgFiske = false,
            erGrenseArbeider = false,
            harAvsluttetArbeidsforholdFraKonkurs = true,
            oppfyllerMinsteinntekt = false,
            koronaRegelverkForMinsteinntektBrukt = false,
        ) shouldBe OppgaveBenk("4401", "Konkurs\n")
    }

    @Test
    fun `Finn riktig oppgave beskrivelse og benk når søker er permittert fra fiskeforedling  `() {
        beregnOppgaveBenk(
            harEøsArbeidsforhold = false,
            harInntektFraFangstOgFiske = false,
            erGrenseArbeider = false,
            harAvsluttetArbeidsforholdFraKonkurs = false,
            oppfyllerMinsteinntekt = false,
            koronaRegelverkForMinsteinntektBrukt = true,
            behandlendeEnhet = "4450",
            erPermittertFraFiskeforedling = true
        ) shouldBe OppgaveBenk("4454", "FISK\n")
    }

    @Test
    fun `Finn riktig oppgave beskrivelse og benk ved oppfyller minsteinntekt ved ordninær   `() {
        beregnOppgaveBenk(
            harEøsArbeidsforhold = false,
            harInntektFraFangstOgFiske = false,
            erGrenseArbeider = false,
            harAvsluttetArbeidsforholdFraKonkurs = false,
            oppfyllerMinsteinntekt = false,
            koronaRegelverkForMinsteinntektBrukt = false,
            behandlendeEnhet = "4450",
            erPermittertFraFiskeforedling = false
        ) shouldBe OppgaveBenk("4451", "Minsteinntekt - mulig avslag\n")
    }

    @Test
    fun `Finn riktig oppgave beskrivelse og benk ved oppfyller minsteinntekt ved permittering   `() {
        beregnOppgaveBenk(
            harEøsArbeidsforhold = false,
            harInntektFraFangstOgFiske = false,
            erGrenseArbeider = false,
            harAvsluttetArbeidsforholdFraKonkurs = false,
            oppfyllerMinsteinntekt = false,
            koronaRegelverkForMinsteinntektBrukt = false,
            behandlendeEnhet = "4455",
            erPermittertFraFiskeforedling = false,
        ) shouldBe OppgaveBenk("4456", "Minsteinntekt - mulig avslag\n")
    }

    @Test
    fun `Finn riktig oppgave beskrivelse og benk ved oppfyller minsteinntekt ved korona regler   `() {
        beregnOppgaveBenk(
            harEøsArbeidsforhold = false,
            harInntektFraFangstOgFiske = false,
            erGrenseArbeider = false,
            harAvsluttetArbeidsforholdFraKonkurs = false,
            oppfyllerMinsteinntekt = false,
            koronaRegelverkForMinsteinntektBrukt = true,
            behandlendeEnhet = "4450",
            erPermittertFraFiskeforedling = false,
        ) shouldBe OppgaveBenk("4451", "Minsteinntekt - mulig avslag - korona\n")
    }

    @Test
    fun ` Finn riktig oppgavebeskrivelse ved ny søknad `() {
        beregnOppgaveBenk(
            harEøsArbeidsforhold = false,
            harInntektFraFangstOgFiske = false,
            erGrenseArbeider = false,
            harAvsluttetArbeidsforholdFraKonkurs = false,
            oppfyllerMinsteinntekt = true,
            koronaRegelverkForMinsteinntektBrukt = false,
            behandlendeEnhet = "4450",
            henvendelseType = "NY_SØKNAD",
            erPermittertFraFiskeforedling = false,
        ) shouldBe OppgaveBenk("4450", "Start Vedtaksbehandling - automatisk journalført.\n")
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

    @Test
    fun `kan lese fangst og fisk faktum fra packet`() {
        Packet().apply { putValue("søknadsdata", "soknadsdata.json".getJsonResource()) }.also { packet ->
            packet.harInntektFraFangstOgFiske() shouldBe true
        }
    }

    @Test
    fun `kan lese jobbet i eøs faktum fra packet`() {
        Packet().apply { putValue("søknadsdata", "soknadsdata.json".getJsonResource()) }.also { packet ->
            packet.harEøsArbeidsforhold() shouldBe true
        }
    }

    @Test
    fun `kan lese avtjent verneplikt fra packet`() {
        Packet().apply { putValue("søknadsdata", "soknadsdata.json".getJsonResource()) }.also { packet ->
            packet.harAvtjentVerneplikt() shouldBe true
        }
    }

    @Test
    fun `kan lese språk fra packet`() {
        Packet().apply { putValue("søknadsdata", "soknadsdata.json".getJsonResource()) }.also { packet ->
            packet.språk() shouldBe "nb_NO"
        }
    }

    @Test
    fun `kan lese antall arbeidsforhold fra packet`() {
        Packet().apply { putValue("søknadsdata", "soknadsdata.json".getJsonResource()) }.also { packet ->
            packet.antallArbeidsforhold() shouldBe 2
        }
    }

    @Test
    fun `kan lese andre ytelser fra packet`() {
        Packet().apply { putValue("søknadsdata", "soknadsdata.json".getJsonResource()) }.also { packet ->
            packet.andreYtelser() shouldBe false
        }
    }

    @Test
    fun `kan lese egen næring fra packet`() {
        Packet().apply { putValue("søknadsdata", "soknadsdata.json".getJsonResource()) }.also { packet ->
            packet.egenNæring() shouldBe true
        }
    }

    @Test
    fun `kan lese arbeidstilstand fra packet`() {
        Packet().apply { putValue("søknadsdata", "soknadsdata.json".getJsonResource()) }.also { packet ->
            packet.arbeidstilstand() shouldBe "fastArbeidstid"
        }
    }

    @Test
    fun `kan lese utdanning fra packet`() {
        Packet().apply { putValue("søknadsdata", "soknadsdata.json".getJsonResource()) }.also { packet ->
            packet.utdanning() shouldBe "ikkeUtdanning"
        }
    }
    @Test
    fun `kan lese reell arbeidssøker fra packet`() {
        Packet().apply { putValue("søknadsdata", "soknadsdata.json".getJsonResource()) }.also { packet ->
            packet.reellArbeidssøker().villigAlle shouldBe true
            packet.reellArbeidssøker()["villigdeltid"] shouldBe true
        }
    }

    @Test
    fun `gir null om datapunktet mangler fra packet`() {
        Packet().apply { putValue("søknadsdata", emptyMap<String?, String?>()) }.also { packet ->
            packet.utdanning() shouldBe null
        }
    }

    @Test
    fun `kan lese fornyetrettighet `() {
        Packet().apply { putValue("søknadsdata", "soknadsdata.json".getJsonResource()) }.also { packet ->
            packet.fornyetRettighet() shouldBe true
        }
        Packet().apply { putValue("søknadsdata", emptyMap<String?, String?>()) }.also { packet ->
            packet.fornyetRettighet() shouldBe false
        }
    }
}
