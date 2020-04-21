package no.nav.dagpenger.journalføring.ferdigstill.adapter.vilkårtester

import com.github.kittinunf.fuel.core.Request

internal fun Request.apiKey(apiKey: String) = this.header("X-API-KEY", apiKey)
