package no.nav.dagpenger.journalføring.ferdigstill.adapter

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals

internal class ArenaTilleggsinformasjonTest {
    val dato = LocalDateTime.parse("2019-12-24T12:01:57").atZone(ZoneId.of("Europe/Oslo"))
    @Test
    fun `lager riktig tilleggsinformasjon når den får hoveddokument og vedlegg`() {
        val tilleggsinformasjon =
                createArenaTilleggsinformasjon(listOf("Søknad", "Vedlegg: Arbeidsavtale"), dato)

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
                createArenaTilleggsinformasjon(listOf("Søknad"), dato)

        assertEquals(
                "Hoveddokument: Søknad\n" +
                        "Registrert dato: 24.12.2019\n" +
                        "Dokumentet er skannet inn og journalført automatisk av digitale dagpenger. " +
                        "Gjennomfør rutinen \"Etterkontroll av automatisk journalførte dokumenter\".", tilleggsinformasjon
        )
    }
}