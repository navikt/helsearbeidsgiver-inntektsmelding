package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.db.ForespoerselRepository
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class HentOrgnrLoeser(
    rapid: RapidsConnection,
    private val forespoerselRepo: ForespoerselRepository
) : River.PacketListener {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    init {
        River(rapid).apply {
            validate {
                it.demandKey(Key.EVENT_NAME.str)
                it.demandValues(
                    Key.BEHOV to BehovType.HENT_IM_ORGNR.name
                )
                it.requireKeys(
                    Key.UUID,
                    DataFelt.FORESPOERSEL_ID
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val json = packet.toJson().parseJson()

        logger.info("Mottok melding med behov '${BehovType.HENT_IM_ORGNR}'.")
        sikkerLogger.info("Mottok melding:\n${json.toPretty()}")

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.behov(BehovType.HENT_IM_ORGNR)
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
                    hentOrgnr(it, context)
                }
                    .onFailure { feil ->
                        context.publishFeil("Ukjent feil.", feil, it)
                    }
            }
        }
    }

    private fun hentOrgnr(melding: Melding, context: MessageContext) {
        runCatching {
            forespoerselRepo.hentOrgnr(melding.forespoerselId)
        }
            .onSuccess { orgnr ->
                if (orgnr != null) {
                    context.publishSuksess(orgnr, melding)
                } else {
                    context.publishFeil("Fant ingen orgnr for forespørsel-ID '${melding.forespoerselId}'.", null, melding)
                }
            }
            .onFailure { feil ->
                context.publishFeil("Klarte ikke hente lagret orgnr for forespørsel-ID '${melding.forespoerselId}'.", feil, melding)
            }
    }

    private fun MessageContext.publishSuksess(orgnr: String, melding: Melding) {
        "Fant orgnr.".also {
            logger.info(it)
            sikkerLogger.info("$it orgnr='$orgnr'")
        }

        publish(
            Key.EVENT_NAME to melding.event.toJson(),
            Key.DATA to "".toJson(),
            Key.UUID to melding.transaksjonId.toJson(),
            DataFelt.ORGNRUNDERENHET to orgnr.toJson()
        )
            .also {
                logger.info("Publiserte data for '${BehovType.HENT_IM_ORGNR}'.")
                sikkerLogger.info("Publiserte data:\n${it.toPretty()}")
            }
    }

    private fun MessageContext.publishFeil(feilmelding: String, feil: Throwable?, melding: Melding?) {
        logger.error("$feilmelding Se sikker logg for mer info.")
        sikkerLogger.error(feilmelding, feil)

        Fail(
            eventName = melding?.event,
            behov = BehovType.HENT_IM_ORGNR,
            feilmelding = feilmelding,
            forespørselId = null,
            uuid = melding?.transaksjonId?.toString()
        )
            .toJsonMessage()
            .toJson()
            .also(::publish)
            .also {
                logger.error("Publiserte feil for ${BehovType.HENT_IM_ORGNR}.")
                sikkerLogger.error("Publiserte feil:\n${it.parseJson().toPretty()}")
            }
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        "Innkommende melding har feil.".let {
            logger.error("$it Se sikker logg for mer info.")
            sikkerLogger.error("$it Detaljer:\n${problems.toExtendedReport()}")
        }
    }
}

private data class Melding(
    val event: EventName,
    val transaksjonId: UUID,
    val forespoerselId: UUID
) {
    companion object {
        fun fra(json: JsonElement): Melding =
            json.toMap().let {
                Melding(
                    event = Key.EVENT_NAME.les(EventName.serializer(), it),
                    transaksjonId = Key.UUID.les(UuidSerializer, it),
                    forespoerselId = DataFelt.FORESPOERSEL_ID.les(UuidSerializer, it)
                )
            }
    }

    fun withLogFields(block: (Melding) -> Unit) {
        MdcUtils.withLogFields(
            Log.event(event),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId)
        ) {
            block(this)
        }
    }
}
