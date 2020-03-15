package no.nav.dagpenger.journalføring.ferdigstill.adapter

import mu.KotlinLogging
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger { }
private const val maksTegn = 1999

fun createArenaTilleggsinformasjon(dokumentTitler: List<String>, registrertDato: ZonedDateTime): String {
    val hovedDokument = dokumentTitler.first()
    val vedlegg = dokumentTitler.drop(1)

    val formatertVedlegg =
        if (vedlegg.isNotEmpty()) {
            vedlegg.joinToString(prefix = "- ", separator = "\n- ", postfix = "\n")
        } else {
            ""
        }
    val formatertDato = registrertDato.toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    val datoBeskrivelse = "Registrert dato: ${formatertDato}\n"

    val informasjon = "Hoveddokument: ${hovedDokument}\n" +
        formatertVedlegg +
        datoBeskrivelse +
        "Dokumentet er skannet inn og journalført automatisk av digitale dagpenger. Gjennomfør rutinen \"Etterkontroll av automatisk journalførte dokumenter\"."

    return if (informasjon.length > maksTegn) {
        logger.warn { "Tillegsinformasjon over 1999 tegn, var ${informasjon.length}}" }
        "Hoveddokument: ${hovedDokument}\nRegistrert dato: ${formatertDato}\nDokumentet er skannet inn og journalført automatisk av digitale dagpenger. Gjennomfør rutinen \"Etterkontroll av automatisk journalførte dokumenter\"."
    } else {
        informasjon
    }
}
