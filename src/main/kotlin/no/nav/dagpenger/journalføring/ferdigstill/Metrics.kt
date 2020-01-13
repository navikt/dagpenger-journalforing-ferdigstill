package no.nav.dagpenger.journalføring.ferdigstill

import io.prometheus.client.Counter

internal object Metrics {

    private val labelNames = listOf(
        "saksType"
    )
    private val DAGPENGER_NAMESPACE = "dagpenger"

    private val jpFerdigstiltCounter = Counter
        .build()
        .namespace(DAGPENGER_NAMESPACE)
        .name("journalpost_ferdigstilt")
        .help("Number of journal post processed succesfully")
        .labelNames(*labelNames.toTypedArray())
        .register()

    fun jpFerdigStillInc(saksType: SaksType) = jpFerdigstiltCounter.labels(saksType.name.toLowerCase()).inc()

    val automatiskJournalførtTeller = Counter
        .build()
        .name("automatisk_journalfort_arena")
        .help("Antall søknader som er automatisk journalført i Arena")
        .labelNames("opprettet", "grunn")
        .register()

    val automatiskJournalførtJaTeller = automatiskJournalførtTeller.labels("true", "arena_ok")
    fun automatiskJournalførtNeiTeller(reason: String) = automatiskJournalførtTeller.labels("false", reason).inc()

    val antallDagpengerSaker = Counter
        .build()
        .name("dagpenger_saker")
        .help("Antall dagpengesaker basert på status")
        .labelNames("status")
        .register()

    val aktiveDagpengeSakTeller = antallDagpengerSaker.labels("aktiv")
    val avsluttetDagpengeSakTeller = antallDagpengerSaker.labels("avsluttet")
    val inaktivDagpengeSakTeller = antallDagpengerSaker.labels("inaktiv")
}
