package no.nav.dagpenger.journalføring.ferdigstill

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.cache2k.Cache2kBuilder
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class Behov {
    Medlemskap
}

abstract class BehovRiver(
    private val rapidsConnection: RapidsConnection,
    private val behov: List<Behov>) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@final", true) }
            validate { it.requireKey("@id") } // { ULID.parseULID(it.asText()) }
            validate { it.requireKey("@løsning") }
            validate { it.demandAll("@behov", behov.map(Enum<*>::name)) }
        }.register(this)
    }

    private val cache =
        object : Cache2kBuilder<String, JsonMessage>() {}
            .name("behovCache")
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .entryCapacity(5000)
            .build()

    private val idCache = mutableSetOf<String>()

    internal fun opprettBehov(parametre: Map<String, String>): String {
        val id = UUID.randomUUID().toString()
        val behov = mapOf(
            "@id" to id,
            "@behov" to behov.map { it.name },
            "@event_name" to "behov"
        ) + parametre
        rapidsConnection.publish(JsonMessage.newMessage(behov).toJson())
        idCache.add(id)
        return id
    }

    internal fun hentSvar(id: String): JsonMessage {
        return cache[id] ?: throw RuntimeException("Fant ikke behov")
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        if (packet["@id"].asText() in idCache) {
            cache.put(packet["@id"].asText(), packet)
        }
    }

    override fun onError(problems: MessageProblems, context: RapidsConnection.MessageContext) {
        throw RuntimeException(problems.toExtendedReport())
    }

    override fun onSevere(error: MessageProblems.MessageException, context: RapidsConnection.MessageContext) {
        throw RuntimeException(error)
    }
}
