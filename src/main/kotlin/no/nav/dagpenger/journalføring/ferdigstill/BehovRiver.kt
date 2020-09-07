package no.nav.dagpenger.journalføring.ferdigstill

import de.huxhorn.sulky.ulid.ULID
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.cache2k.Cache2kBuilder
import java.util.concurrent.TimeUnit

enum class Behov {
    Medlemskap
}

private val ulid = ULID()

class BehovRiver<T>(
    private val rapidsConnection: RapidsConnection,
    private val behov: List<Behov>,
    private val parseSvar: (JsonMessage) -> T
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@final", true) }
            validate { it.require("@id") { id -> ULID.parseULID(id.asText()) } }
            validate { it.requireKey("@løsning") }
            validate { it.demandAll("@behov", behov.map(Enum<*>::name)) }
        }.register(this)
    }

    private val packetCache =
        object : Cache2kBuilder<String, JsonMessage>() {}
            .name("behov-cache")
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .entryCapacity(5000)
            .build()

    private val idCache =
        object : Cache2kBuilder<String, JsonMessage>() {}
            .name("id-cache")
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .entryCapacity(5000)
            .build()

    internal fun opprettBehov(parametre: Map<String, String>): String {
        val id = ulid.nextULID()
        val behov = JsonMessage.newMessage(
            mapOf(
                "@id" to id,
                "@behov" to behov.map { it.name },
                "@event_name" to "behov"
            ) + parametre
        )
        rapidsConnection.publish(behov.toJson())
        idCache.put(id, behov)
        return id
    }

    internal fun hentSvar(id: String): T {
        packetCache[id].let {
            return parseSvar(it)
        }
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        val id = packet["@id"].asText()
        if (idCache.containsKey(id)) {
            packetCache.put(id, packet)
        }
    }

    override fun onError(problems: MessageProblems, context: RapidsConnection.MessageContext) {
        throw RuntimeException(problems.toExtendedReport())
    }

    override fun onSevere(error: MessageProblems.MessageException, context: RapidsConnection.MessageContext) {
        throw RuntimeException(error)
    }
}
