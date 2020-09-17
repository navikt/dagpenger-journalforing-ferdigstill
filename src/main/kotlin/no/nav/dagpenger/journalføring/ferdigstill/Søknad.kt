package no.nav.dagpenger.journalføring.ferdigstill

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.dagpenger.events.Packet


interface Søknad {
    fun getFakta(faktaNavn: String): List<JsonNode>
    fun getBooleanFaktum(faktaNavn: String): Boolean
    fun getBooleanFaktum(faktaNavn: String, defaultValue: Boolean): Boolean
    fun getChildFakta(faktumId: Int): List<JsonNode>
}

fun Packet.getSøknad(): Søknad? {
    return if (this.hasField("søknaddata")) {
        fromMap(this.getMapValue("søknaddata"))
    } else null
}

private fun fromMap(json: Map<String, Any>): Søknad {
    val objectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(JavaTimeModule())

    return objectMapper.convertValue(json, object : TypeReference<JsonNode>() {}).let {
        val søknadAsJson = it

        object : Søknad {
            override fun getFakta(faktaNavn: String): List<JsonNode> =
                søknadAsJson.get("fakta").filter { it["key"].asText() == faktaNavn }

            override fun getBooleanFaktum(faktaNavn: String) = getFaktumValue(
                getFakta(faktaNavn)
            ).asBoolean()

            override fun getBooleanFaktum(faktaNavn: String, defaultValue: Boolean) = kotlin.runCatching {
                getFaktumValue(
                    getFakta(faktaNavn)
                ).asBoolean()
            }.getOrDefault(defaultValue)

            override fun getChildFakta(faktumId: Int): List<JsonNode> =
                søknadAsJson.get("fakta").filter { it["parrentFaktum"].asInt() == faktumId }

            private fun getFaktumValue(fakta: List<JsonNode>): JsonNode = fakta
                .first()
                .get("value")
        }
    }
}
