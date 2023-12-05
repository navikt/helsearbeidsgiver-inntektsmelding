package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demand
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class ForespoerselSvarLoeser(rapid: RapidsConnection) : River.PacketListener {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    init {
        River(rapid).apply {
            validate { msg ->
                msg.demandValues(
                    Pri.Key.BEHOV to ForespoerselSvar.behovType.name
                )
                msg.demand(
                    Pri.Key.LØSNING to { it.fromJson(ForespoerselSvar.serializer()) }
                )
                msg.interestedIn(Pri.Key.FORESPOERSEL_ID.str)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val json = packet.toJson().parseJson()

        logger.info("Mottok løsning på pri-topic om ${ForespoerselSvar.behovType}.")
        sikkerLogger.info("Mottok løsning på pri-topic:\n${json.toPretty()}")

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.behov(BehovType.HENT_TRENGER_IM)
        ) {
            val melding = runCatching {
                Melding.fra(json)
            }
                .onFailure {
                    context.publishFeil("Klarte ikke lese påkrevde felt fra innkommende melding.", it, null)
                }
                .getOrNull()

            melding?.withLogFields {
                runCatching {
                    sendSvar(it, context)
                }
                    .onFailure { feil ->
                        context.publishFeil("Ukjent feil.", feil, it)
                    }
            }
        }
    }

    private fun sendSvar(melding: Melding, context: MessageContext) {
        if (melding.forespoerselSvar.resultat != null) {
            val forespoersel = melding.forespoerselSvar.resultat.toTrengerInntekt()
            context.publishSuksess(forespoersel, melding)
        } else {
            val feilmelding = if (melding.forespoerselSvar.feil != null) {
                "Klarte ikke hente forespørsel. Feilet med kode '${melding.forespoerselSvar.feil}'."
            } else {
                "Svar fra bro-appen har hverken resultat eller feil."
            }

            context.publishFeil(feilmelding, null, melding)
        }
    }

    private fun MessageContext.publishSuksess(forespoersel: TrengerInntekt, melding: Melding) {
        publish(
            Key.EVENT_NAME to melding.initiateEvent.toJson(),
            Key.DATA to "".toJson(),
            Key.UUID to melding.transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to melding.forespoerselSvar.forespoerselId.toJson(),
            Key.FORESPOERSEL_SVAR to forespoersel.toJson(TrengerInntekt.serializer())
        )
            .also {
                logger.info("Publiserte data for ${BehovType.HENT_TRENGER_IM}.")
                sikkerLogger.info("Publiserte data:\n${it.toPretty()}")
            }
    }

    private fun MessageContext.publishFeil(feilmelding: String, feil: Throwable?, melding: Melding?) {
        logger.error("$feilmelding Se sikker logg for mer info.")
        sikkerLogger.error(feilmelding, feil)

        Fail(
            eventName = melding?.initiateEvent,
            behov = BehovType.HENT_TRENGER_IM,
            feilmelding = feilmelding,
            forespørselId = melding?.forespoerselSvar?.forespoerselId?.toString(),
            uuid = melding?.transaksjonId?.toString()
        )
            .toJsonMessage()
            .toJson()
            .also(::publish)
            .also {
                logger.warn("Publiserte feil for ${BehovType.HENT_TRENGER_IM}.")
                sikkerLogger.warn("Publiserte feil:\n${it.parseJson().toPretty()}")
            }
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        "Innkommende melding har feil.".let {
            logger.error("$it Se sikker logg for mer info.")
            sikkerLogger.error("$it Detaljer:\n${problems.toExtendedReport()}")
        }
    }
}

fun ForespoerselSvar.Suksess.toTrengerInntekt(): TrengerInntekt =
    TrengerInntekt(
        type = type,
        orgnr = orgnr,
        fnr = fnr,
        skjaeringstidspunkt = skjaeringstidspunkt,
        sykmeldingsperioder = sykmeldingsperioder,
        egenmeldingsperioder = egenmeldingsperioder,
        forespurtData = forespurtData,
        erBesvart = erBesvart
    )

private data class Melding(
    val initiateEvent: EventName,
    val transaksjonId: UUID,
    val forespoerselSvar: ForespoerselSvar
) {
    companion object {
        fun fra(json: JsonElement): Melding =
            json.toMap().let {
                val forespoerselSvar = Pri.Key.LØSNING.les(ForespoerselSvar.serializer(), it)
                val boomerang = forespoerselSvar.boomerang.toMap()

                Melding(
                    initiateEvent = Key.EVENT_NAME.les(EventName.serializer(), boomerang),
                    transaksjonId = Key.UUID.les(UuidSerializer, boomerang),
                    forespoerselSvar = forespoerselSvar
                )
            }
    }

    fun withLogFields(block: (Melding) -> Unit) {
        MdcUtils.withLogFields(
            Log.event(initiateEvent),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselSvar.forespoerselId)
        ) {
            block(this)
        }
    }
}
