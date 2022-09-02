package no.nav.helsearbeidsgiver.inntektsmelding.akkumulator

import io.lettuce.core.RedisClient
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.slf4j.LoggerFactory

class Akkumulator(rapidsConnection: RapidsConnection, private val redisUrl: String) : River.PacketListener {
    private val log = LoggerFactory.getLogger(this::class.java)

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandKey("@løsning")
                it.requireKey("@id")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val id = packet["@id"].asText()
        val løsning = packet["@løsning"].toString()

        log.info("Akkumulerer løsning med id $id")

        val redisClient = RedisClient.create("redis://$redisUrl:6379/0")
        val connection = redisClient.connect()
        val syncCommands = connection.sync()

        syncCommands[id] = løsning

        connection.close()
        redisClient.shutdown()
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {}
}
