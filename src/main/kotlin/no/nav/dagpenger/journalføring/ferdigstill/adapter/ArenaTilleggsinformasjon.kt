package no.nav.dagpenger.journalføring.arena.adapter

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

fun createArenaTilleggsinformasjon(dokumentTitler: List<String>, registrertDato: String): String {
    val hovedDokument = dokumentTitler.first()
    val vedlegg = dokumentTitler.drop(1)

    val formatertVedlegg =
        if (vedlegg.isNotEmpty()) {
            vedlegg.joinToString(prefix = "- ", separator = "\n- ", postfix = "\n")
        } else {
            ""
        }

    val formatertDato = formattedDateOrNull(registrertDato)?.let { "Registrert dato: ${it}\n" } ?: ""

    return "Hoveddokument: ${hovedDokument}\n" +
        formatertVedlegg +
        formatertDato +
        "Dokumentet er skannet inn og journalført automatisk av digitale dagpenger. Gjennomfør rutinen \"Etterkontroll av automatisk journalførte dokumenter\"."
}

fun formattedDateOrNull(dato: String): String? {
    return try {
        LocalDateTime.parse(dato).toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
    } catch (e: DateTimeParseException) {
        null
    }
}