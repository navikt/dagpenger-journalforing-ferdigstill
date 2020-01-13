package no.nav.dagpenger.journalføring.ferdigstill.adapter

import no.nav.dagpenger.journalføring.arena.adapter.createArenaTilleggsinformasjon
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

internal class ArenaTilleggsinformasjonTest {
    @Test
    fun `lager riktig tilleggsinformasjon når den får hoveddokument og vedlegg`() {
        val tilleggsinformasjon =
            createArenaTilleggsinformasjon(listOf("Søknad", "Vedlegg: Arbeidsavtale"), "2019-12-24T12:01:57")

        assertEquals(
            "Hoveddokument: Søknad\n" +
                "- Vedlegg: Arbeidsavtale\n" +
                "Registrert dato: 24.12.2019\n" +
                "Dokumentet er skannet inn og journalført automatisk av digitale dagpenger. " +
                "Gjennomfør rutinen \"Etterkontroll av automatisk journalførte dokumenter\".", tilleggsinformasjon
        )
    }

    @Test
    fun `formaterer riktig når vedlegg mangler`() {
        val tilleggsinformasjon =
            createArenaTilleggsinformasjon(listOf("Søknad"), "2019-12-24T12:01:57")

        assertEquals(
            "Hoveddokument: Søknad\n" +
                "Registrert dato: 24.12.2019\n" +
                "Dokumentet er skannet inn og journalført automatisk av digitale dagpenger. " +
                "Gjennomfør rutinen \"Etterkontroll av automatisk journalførte dokumenter\".", tilleggsinformasjon
        )
    }

    @Test
    fun `legger ikke til dato hvis den ikke er formatert etter ISO-standard`() {
        val tilleggsinformasjon =
            createArenaTilleggsinformasjon(listOf("Søknad"), "2019.12.24")

        assertEquals(
            "Hoveddokument: Søknad\n" +
                "Dokumentet er skannet inn og journalført automatisk av digitale dagpenger. " +
                "Gjennomfør rutinen \"Etterkontroll av automatisk journalførte dokumenter\".", tilleggsinformasjon
        )
    }
}