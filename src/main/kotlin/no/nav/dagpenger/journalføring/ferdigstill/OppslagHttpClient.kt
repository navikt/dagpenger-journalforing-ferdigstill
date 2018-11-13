package no.nav.dagpenger.journalføring.ferdigstill

import com.github.kittinunf.fuel.gson.responseObject
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result

class OppslagHttpClient(private val oppslagUrl: String) {

    fun ferdigstillJournalføring(journalpostId: String) {
        val url = "${oppslagUrl}joark/ferdigstill"
        val (_, response, result) = with(url.httpPost().body(journalpostId)) {
            responseObject<String>()
        }
        return when (result) {
            is Result.Failure -> throw OppslagException(
                    response.statusCode, response.responseMessage, result.getException())
            is Result.Success -> Unit
        }
    }
}

class OppslagException(val statusCode: Int, override val message: String, override val cause: Throwable) : RuntimeException(message, cause)