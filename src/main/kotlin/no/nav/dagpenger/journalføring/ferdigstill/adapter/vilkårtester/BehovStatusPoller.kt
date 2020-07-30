package no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester

import com.github.kittinunf.fuel.httpGet
import io.prometheus.client.Counter
import io.prometheus.client.Histogram
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.time.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mu.KotlinLogging
import no.nav.dagpenger.journalføring.ferdigstill.adapter.responseObject
import java.time.Duration
import java.util.concurrent.Executors

private val LOGGER = KotlinLogging.logger {}
private val api = Executors.newFixedThreadPool(4).asCoroutineDispatcher()

private val timeSpentPolling = Histogram.build()
    .name("time_spent_polling_status")
    .help("Time spent polling status on behov")
    .register()

private val timesPolled = Counter.build()
    .name("times_polled_status")
    .help("Times we needed to pull for status on behov")
    .register()

class BehovStatusPoller(
    private val regelApiUrl: String,
    private val regelApiKey: String,
    private val timeout: Duration = Duration.ofSeconds(20)
) {
    private val delayDuration = Duration.ofMillis(100)

    private fun pollInternal(statusUrl: String): BehovStatusPollResult {
        val (_, response, result) =
            with(
                statusUrl
                    .httpGet()
                    .apiKey(regelApiKey)
                    .allowRedirects(false)
            ) { responseObject<BehovStatusResponse>() }

        return try {
            BehovStatusPollResult(result.get().status, null)
        } catch (exception: Exception) {
            if (response.statusCode == 303) {
                LOGGER.info("Caught 303: $response")
                return BehovStatusPollResult(
                    null,
                    response.headers["Location"].first()
                )
            } else {
                throw PollSubsumsjonStatusException(
                    response.responseMessage, exception
                )
            }
        }
    }

    suspend fun pollStatus(statusUrl: String): String {
        val url = "$regelApiUrl$statusUrl"

        return withContext(api) {
            try {
                return@withContext withTimeout(timeout.toMillis()) {
                    return@withTimeout pollWithDelay(url)
                }
            } catch (e: Exception) {
                when (e) {
                    is TimeoutCancellationException -> throw RegelApiTimeoutException("Polled behov status for more than ${timeout.toMillis()} milliseconds")
                    else -> throw PollSubsumsjonStatusException("Failed", e)
                }
            }
        }
    }

    private suspend fun pollWithDelay(statusUrl: String): String {
        val status = pollInternal(statusUrl)
        return if (status.isPending()) {
            delay(delayDuration)
            pollWithDelay(statusUrl)
        } else {
            status.location ?: throw PollSubsumsjonStatusException("Did not get location with task")
        }
    }
}

private data class BehovStatusPollResult(
    val status: BehovStatus?,
    val location: String?
) {
    fun isPending() = status == BehovStatus.PENDING
}

class PollSubsumsjonStatusException(
    override val message: String,
    override val cause: Throwable? = null
) : RuntimeException(message, cause)

class RegelApiTimeoutException(override val message: String) : RuntimeException(message)
