package no.nav.helsearbeidsgiver.inntektsmelding.dirigent

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.ApiBehov
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.log.logger
import no.nav.helsearbeidsgiver.felles.log.loggerSikker
import no.nav.helsearbeidsgiver.felles.message.Løsninger
import no.nav.helsearbeidsgiver.felles.rapidsrivers.require
import java.util.UUID

class ApiBehovRiver(
    rapid: RapidsConnection,
    private val mockRedisStore: MockRedisStore
) : River.PacketListener {
    private val logger = logger()
    private val loggerSikker = loggerSikker()

    init {
        River(rapid).apply {
            validate { msg ->
                // TODO burde demande på behov.behov
                msg.require(
                    // TODO burde dette være noe annet enn BEHOV?
                    Key.BEHOV to { it.fromJson(ApiBehov.serializer(ApiBehov.Input.serializer())) }
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val apiBehov = Key.BEHOV.fra(packet).fromJson(ApiBehov.serializer(ApiBehov.Input.serializer()))

        val initiateId = UUID.randomUUID()

        "Mottok behov '${apiBehov::class.simpleName}'. Knyttet behov til generert ID '$initiateId'.".let {
            logger.info(it)
            loggerSikker.info("$it Fullt behov:\n$apiBehov.")
        }

        val løsningerMedInput = Løsninger(
            mapOf(
                // TODO fjern !!
                apiBehov.input::class.simpleName!! to apiBehov.input.toJson(ApiBehov.Input.serializer())
            )
        )

        mockRedisStore.lagrePlan(initiateId, apiBehov.plan)
        mockRedisStore.lagreWipSvar(initiateId, løsningerMedInput)

        logger.info("API-behov lagret i redis med id $initiateId")

        val startplan = apiBehov.plan.finnFørsteUfullførteDelplan(emptySet())
            ?: throw IllegalArgumentException("Plan er tom.")

        context.publishAll(startplan, løsningerMedInput)

        logger.info("Publiserte behov for startplan: $startplan")
    }
}
