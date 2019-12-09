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
}
