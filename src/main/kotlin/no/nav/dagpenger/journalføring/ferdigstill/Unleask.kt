package no.nav.dagpenger.journalføring.ferdigstill

import no.finn.unleash.strategy.Strategy

internal class ByClusterStrategy(private val currentCluster: Cluster) : Strategy {
    override fun getName(): String = "byCluster"

    override fun isEnabled(parameters: Map<String, String>?): Boolean {
        val clustersParameter = parameters?.get("cluster") ?: return false
        val alleClustere = clustersParameter.split(",").map { it.trim() }.map { it.toLowerCase() }.toList()
        return alleClustere.contains(currentCluster.asString())
    }

    enum class Cluster {
        DEV_FSS, PROD_FSS, ANNET;

        companion object {
            val current: Cluster by lazy {
                when (System.getenv("NAIS_CLUSTER_NAME")) {
                    "dev-fss" -> DEV_FSS
                    "prod-fss" -> PROD_FSS
                    else -> ANNET
                }
            }
        }

        fun asString(): String = name.toLowerCase().replace("_", "-")
    }
}
