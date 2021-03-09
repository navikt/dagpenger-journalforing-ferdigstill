package no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester

import com.github.kittinunf.fuel.httpGet
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.time.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.Duration
import java.util.concurrent.Executors

private val api = Executors.newFixedThreadPool(4).asCoroutineDispatcher()

class BehovStatusPoller(
    private val regelApiUrl: String,
    private val regelApiKey: String,
    private val timeout: Duration = Duration.ofSeconds(20)
) {
    private val delayDuration = Duration.ofMillis(100)

    private fun pollInternal(statusUrl: String): BehovStatusPollResult {
        val (_, response, result) =
            statusUrl
                .httpGet()
                .apiKey(regelApiKey)
                .allowRedirects(false)
                .response()

        return result.fold(
            {
                when (response.statusCode) {
                    303 -> BehovStatusPollResult(
                        status = false,
                        location = response.headers["Location"].first()
                    )
                    else -> {
                        BehovStatusPollResult(
                            status = true,
                            location = null
                        )
                    }
                }
            },
            {
                throw PollSubsumsjonStatusException(
                    "Failed to poll status behov. Response message ${response.responseMessage}. Error message: ${it.message}. "
                )
            }
        )
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
    val status: Boolean,
    val location: String?
) {
    fun isPending() = status
}

class PollSubsumsjonStatusException(
    override val message: String,
    override val cause: Throwable? = null
) : RuntimeException(message, cause)

class RegelApiTimeoutException(override val message: String) : RuntimeException(message)
