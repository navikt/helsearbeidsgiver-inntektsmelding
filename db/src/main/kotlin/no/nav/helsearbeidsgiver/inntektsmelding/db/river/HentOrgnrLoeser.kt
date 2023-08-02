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
import no.nav.helsearbeidsgiver.felles.IKey
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
                    DataFelt.FORESPOERSEL_ID,
                    Key.UUID
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val json = packet.toJson().parseJson()

        logger.info("Mottok melding med behov '${BehovType.HENT_IM_ORGNR}'.")
        sikkerLogger.info("Mottok melding:\n${json.toPretty()}")

        val melding = json.toMap()

        val event = Key.EVENT_NAME.les(EventName.serializer(), melding)
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(event),
            Log.behov(BehovType.HENT_IM_ORGNR),
            Log.transaksjonId(transaksjonId)
        ) {
            runCatching {
                melding.hentOrgnr(event, transaksjonId, context)
            }
                .onFailure { feil ->
                    val feilmelding = "Ukjent feil."

                    logger.error("$feilmelding Se sikker logg for mer info.")
                    sikkerLogger.error(feilmelding, feil)

                    context.publishFeil(event, transaksjonId, feilmelding)
                }
        }
    }

    private fun Map<IKey, JsonElement>.hentOrgnr(event: EventName, transaksjonId: UUID, context: MessageContext) {
        val forespoerselId = DataFelt.FORESPOERSEL_ID.les(UuidSerializer, this)

        MdcUtils.withLogFields(
            Log.forespoerselId(forespoerselId)
        ) {
            runCatching { forespoerselRepo.hentOrgnr(forespoerselId) }
                .onSuccess { orgnr ->
                    if (orgnr != null) {
                        sikkerLogger.info("Fant orgnr '$orgnr'.")

                        context.publish(
                            Key.EVENT_NAME to event.toJson(),
                            Key.DATA to "".toJson(),
                            Key.UUID to transaksjonId.toJson(),
                            DataFelt.ORGNRUNDERENHET to orgnr.toJson()
                        )
                            .also {
                                logger.info("Publiserte data for '${BehovType.HENT_IM_ORGNR}'.")
                                sikkerLogger.info("Publiserte data:\n${it.toPretty()}")
                            }
                    } else {
                        val feilmelding = "Fant ingen orgnr for forespørsel-ID '$forespoerselId'."

                        logger.error(feilmelding)
                        sikkerLogger.error(feilmelding)

                        context.publishFeil(event, transaksjonId, feilmelding)
                    }
                }
                .onFailure { feil ->
                    val feilmelding = "Klarte ikke hente lagret orgnr for forespørsel-ID '$forespoerselId'."

                    logger.error(feilmelding)
                    sikkerLogger.error(feilmelding, feil)

                    context.publishFeil(event, transaksjonId, feilmelding)
                }
        }
    }

    private fun MessageContext.publishFeil(event: EventName, transaksjonId: UUID, feilmelding: String) {
        Fail(
            eventName = event,
            behov = BehovType.HENT_IM_ORGNR,
            feilmelding = feilmelding,
            forespørselId = null,
            uuid = transaksjonId.toString()
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
