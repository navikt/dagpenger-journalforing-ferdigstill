package no.nav.dagpenger.journalføring.ferdigstill

import de.huxhorn.sulky.ulid.ULID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.single
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.cache2k.Cache2kBuilder
import java.time.Duration
import java.util.concurrent.TimeUnit

enum class Behov {
    Medlemskap
}

private val ulid = ULID()

private val logger = KotlinLogging.logger {}
private val sikkerLogger = KotlinLogging.logger("tjenestekall")

class BehovRiver(
    private val rapidsConnection: RapidsConnection,
    private val behov: List<Behov>,
    private val attempts: Int = 3,
    private val delay: Duration = Duration.ofSeconds(3)
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
            .name("behov-cache-${ulid.nextULID()}")
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .entryCapacity(5000)
            .build()

    private val idCache =
        object : Cache2kBuilder<String, JsonMessage>() {}
            .name("id-cache-${ulid.nextULID()}")
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .entryCapacity(5000)
            .build()

    internal fun opprettBehov(parametre: Map<String, String>): String {
        val id = ulid.nextULID()
        val forespurtBehov = JsonMessage.newMessage(
            mapOf(
                "@id" to id,
                "@behov" to behov.map { it.name },
                "@event_name" to "behov"
            ) + parametre
        )
        rapidsConnection.publish(forespurtBehov.toJson())
        logger.info { "Produsert behov ${behov.joinToString(separator = ", ")} med id $id" }
        idCache.put(id, forespurtBehov)
        return id
    }

    suspend fun <T> hentSvar(id: String, parseSvar: (JsonMessage) -> T): T {
        val svar = flow {
            if (packetCache.containsKey(id)) emit(parseSvar(packetCache[id]))
            else throw NoSuchElementException("Fant ikke svar på behov med id $id")
        }.retryWhen { cause, attempt ->
            if (cause is NoSuchElementException && attempt <= attempts) {
                delay(delay.toMillis())
                return@retryWhen true
            } else {
                return@retryWhen false
            }
        }

        return svar.single()
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        val id = packet["@id"].asText()
        if (idCache.containsKey(id)) {
            logger.info { "Fått svar på behov $id" }
            packetCache.put(id, packet)
        }
    }

    override fun onError(problems: MessageProblems, context: RapidsConnection.MessageContext) {
        logger.debug { "kunne ikke gjenkjenne melding:\t${problems}\n" }
        sikkerLogger.debug { "ukjent melding:\nProblemer:\t${problems.toExtendedReport()}\n" }
    }

    override fun onSevere(error: MessageProblems.MessageException, context: RapidsConnection.MessageContext) {
        sikkerLogger.debug("ukjent melding:\n\t${error.message}\n\nProblemer:\n${error.problems.toExtendedReport()}")
    }
}
