package no.nav.dagpenger.journalføring.ferdigstill

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.journalføring.ferdigstill.ByClusterStrategy.Cluster.DEV_FSS
import org.junit.jupiter.api.Test

internal class ByClusterStrategyTest {

    @Test
    fun `ByClusterStrategy skal tolke respons fra Unleash riktig`() {
        val byClusterStrategy = ByClusterStrategy(DEV_FSS)
        mapOf(Pair("cluster", "dev-fss,prod-fss")).also {
            byClusterStrategy.isEnabled(it) shouldBe true
        }
        mapOf(Pair("cluster", "prod-fss")).also {
            byClusterStrategy.isEnabled(it) shouldBe false
        }
    }
}
