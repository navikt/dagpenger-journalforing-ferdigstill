package no.nav.dagpenger.journalføring.ferdigstill

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.fail

internal fun String.getJsonResource(): Map<*, *> {
    val objectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(JavaTimeModule())

    return PacketMapperTest::class.java.classLoader.getResource(this)?.readText().let {
        objectMapper.readValue(it, Map::class.java)
    } ?: fail("Resource $this not found.")
}
