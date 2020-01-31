package no.nav.dagpenger.journalføring.ferdigstill.adapter

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.result.Result
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

fun <T : Any> retryFuel(
    times: Int = 3,
    initialDelay: Long = 1000,
    maxDelay: Long = 4000,
    factor: Double = 2.0,
    fuelFunction: () -> Triple<Request, Response, Result<T, FuelError>>
): Triple<Request, Response, Result<T, FuelError>> {
    var currentDelay = initialDelay
    repeat(times - 1) {

        val res = fuelFunction()
        val (_, _, result) = res

        when (result) {
            is Result.Success -> return res
            is Result.Failure -> logger.warn { result.getException() }
        }

        runBlocking { delay(currentDelay) }
        currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
    }
    return fuelFunction() // last attempt
}
