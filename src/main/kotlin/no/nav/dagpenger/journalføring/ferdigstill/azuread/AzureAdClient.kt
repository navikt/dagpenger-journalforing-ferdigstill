package no.nav.dagpenger.journalføring.ferdigstill.azuread

import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logger
import io.ktor.client.features.logging.Logging
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.medlemskap.oppslag.Configuration
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import java.net.ProxySelector
import java.time.Duration
import java.time.LocalDateTime

private val sikkerlogg = KotlinLogging.logger("tjenestekall")

class AzureAdClient(private val config: Configuration.AzureAd) {
    private val client = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            }
        }

        engine {
            customizeClient { setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault())) }
        }

        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) = sikkerlogg.info { message }
            }
            level = LogLevel.ALL
        }
    }

    private var token: OidcToken? = null

    fun token(resourceClientId: String) =
        if (token?.stillValid == true) token!!
        else runBlocking { newToken(resourceClientId).also { token = it } }

    private suspend fun newToken(resourceClientId: String, scopes: Set<String> = setOf(SCOPE_DEFAULT)): OidcToken {
        return client.submitForm(
            formParameters = Parameters.build {
                append("grant_type", GrantType.clientCredentials)
                append("client_id", config.clientId)
                append("client_secret", config.clientSecret)
                append("scope", scopes.mapToApplicationIdUriScopes(resourceClientId))
            },
            encodeInQuery = false,
            url = config.tokenEndpoint
        )
    }

    companion object {
        const val SCOPE_DEFAULT = ".default"

        private fun Set<String>.mapToApplicationIdUriScopes(applicationIdUri: String): String =
            this.joinToString(separator = " ") { scope ->
                "api://${applicationIdUri.removeSuffix("/")}/$scope"
            }
    }
}

internal object GrantType {
    const val clientCredentials = "client_credentials"
}

data class OidcToken(
    val access_token: String,
    val expires_in: Long
) {
    private val expiryTime = LocalDateTime.now().plus(Duration.ofSeconds(expires_in - timeToRefresh))
    val stillValid: Boolean
        get() = LocalDateTime.now().isBefore(expiryTime)

    companion object {
        private val timeToRefresh: Long = 60
    }
}
