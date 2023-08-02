package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class TilgangLoeser(
    rapidsConnection: RapidsConnection,
    private val altinnClient: AltinnClient
) : River.PacketListener {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandKey(Key.EVENT_NAME.str)
                it.demandValues(
                    Key.BEHOV to BehovType.TILGANGSKONTROLL.name
                )
                it.requireKeys(
                    DataFelt.ORGNRUNDERENHET,
                    DataFelt.FNR,
                    Key.UUID
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val json = packet.toJson().parseJson()

        logger.info("Mottok melding med behov '${BehovType.TILGANGSKONTROLL}'.")
        sikkerLogger.info("Mottok melding:\n${json.toPretty()}")

        val melding = json.toMap()

        val event = Key.EVENT_NAME.les(EventName.serializer(), melding)
        val transaksjonId = Key.UUID.les(UuidSerializer, melding)

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(event),
            Log.behov(BehovType.TILGANGSKONTROLL),
            Log.transaksjonId(transaksjonId)
        ) {
            runCatching {
                melding.hentTilgang(event, transaksjonId, context)
            }
                .onFailure { feil ->
                    val feilmelding = "Ukjent feil."

                    logger.error("$feilmelding Se sikker logg for mer info.")
                    sikkerLogger.error(feilmelding, feil)

                    context.publishFeil(event, transaksjonId, feilmelding)
                }
        }
    }

    private fun Map<IKey, JsonElement>.hentTilgang(event: EventName, transaksjonId: UUID, context: MessageContext) {
        val orgnr = DataFelt.ORGNRUNDERENHET.les(String.serializer(), this)
        val fnr = DataFelt.FNR.les(String.serializer(), this)

        runCatching {
            runBlocking {
                altinnClient.harRettighetForOrganisasjon(fnr, orgnr)
            }
        }
            .onSuccess { harTilgang ->
                val tilgang = if (harTilgang) Tilgang.HAR_TILGANG else Tilgang.IKKE_TILGANG

                "Tilgang hentet: '$tilgang'.".also {
                    logger.info(it)
                    sikkerLogger.info("$it orgnr='$orgnr' fnr='$fnr'")
                }

                context.publish(
                    Key.EVENT_NAME to event.toJson(),
                    Key.DATA to "".toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    DataFelt.TILGANG to tilgang.toJson(Tilgang.serializer())
                )
                    .also {
                        logger.info("Publiserte data for '${BehovType.TILGANGSKONTROLL}'.")
                        sikkerLogger.info("Publiserte data:\n${it.toPretty()}")
                    }
            }
            .onFailure {
                val feilmelding = "Feil ved henting av rettigheter fra Altinn."

                logger.error("$feilmelding Se sikker logg for mer info.")
                sikkerLogger.error(feilmelding, it)

                context.publishFeil(event, transaksjonId, feilmelding)
            }
    }

    private fun MessageContext.publishFeil(event: EventName, transaksjonId: UUID, feilmelding: String) {
        Fail(
            eventName = event,
            behov = BehovType.TILGANGSKONTROLL,
            feilmelding = feilmelding,
            forespørselId = null,
            uuid = transaksjonId.toString()
        )
            .toJsonMessage()
            .toJson()
            .also(::publish)
            .also {
                logger.error("Publiserte feil for ${BehovType.TILGANGSKONTROLL}.")
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
