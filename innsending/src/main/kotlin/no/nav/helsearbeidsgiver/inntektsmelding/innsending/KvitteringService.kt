package no.nav.helsearbeidsgiver.inntektsmelding.innsending

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.InnsendtInntektsmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.FailKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullDataKanal
import no.nav.helsearbeidsgiver.felles.rapidsrivers.StatefullEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import java.util.UUID

// TODO : Duplisert mesteparten av InnsendingService, skal trekke ut i super / generisk løsning.
class KvitteringService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore
) : CompositeEventListener() {

    private val logger = logger()

    override val event = EventName.KVITTERING_REQUESTED
    override val startKeys = listOf(
        Key.FORESPOERSEL_ID
    )
    override val dataKeys = listOf(
        Key.INNTEKTSMELDING_DOKUMENT,
        Key.EKSTERN_INNTEKTSMELDING
    )

    init {
        StatefullEventListener(rapid, event, redisStore, startKeys, ::onPacket)
        StatefullDataKanal(rapid, event, redisStore, dataKeys, ::onPacket)
        FailKanal(rapid, event, ::onPacket)
    }

    override fun new(message: JsonMessage) {
        val transaksjonId: String = message[Key.UUID.str].asText()
        val forespoerselId: String = message[Key.FORESPOERSEL_ID.str].asText()

        logger.info("Sender event: ${event.name} for forespørsel $forespoerselId")

        rapid.publish(
            Key.BEHOV to BehovType.HENT_PERSISTERT_IM.toJson(),
            Key.EVENT_NAME to event.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson()
        )
            .also {
                logger.info("Publiserte melding: ${it.toPretty()}")
            }
    }

    override fun inProgress(message: JsonMessage) {
        "Service skal aldri være \"underveis\".".also {
            logger.error(it)
            sikkerLogger.error(it)
        }
    }

    override fun finalize(message: JsonMessage) {
        val transaksjonsId = message[Key.UUID.str].asText().let(UUID::fromString)
        val clientId = redisStore.get(RedisKey.of(transaksjonsId, event))!!.let(UUID::fromString)
        // TODO: Skriv bort fra empty payload hvis mulig
        val im = InnsendtInntektsmelding(
            message[Key.INNTEKTSMELDING_DOKUMENT.str].asText().let { if (it != "{}") it.fromJson(Inntektsmelding.serializer()) else null },
            message[Key.EKSTERN_INNTEKTSMELDING.str].asText().let { if (it != "{}") it.fromJson(EksternInntektsmelding.serializer()) else null }
        ).toJsonStr(InnsendtInntektsmelding.serializer())

        logger.info("Finalize kvittering med transaksjonsId=$transaksjonsId")
        redisStore.set(RedisKey.of(clientId), im)
    }

    override fun onError(message: JsonMessage, fail: Fail) {
        val clientId = redisStore.get(RedisKey.of(fail.transaksjonId, event))
            ?.let(UUID::fromString)

        if (clientId == null) {
            MdcUtils.withLogFields(
                Log.transaksjonId(fail.transaksjonId)
            ) {
                sikkerLogger.error("Forsøkte å terminere, men clientId mangler i Redis. forespoerselId=${fail.forespoerselId}")
            }
        } else {
            logger.info("Terminate kvittering med forespoerselId=${fail.forespoerselId} og transaksjonsId ${fail.transaksjonId}")
            redisStore.set(RedisKey.of(clientId), fail.feilmelding)
        }
    }
}
