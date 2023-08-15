package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import io.prometheus.client.Summary
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
import no.nav.helsearbeidsgiver.felles.Fnr
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Orgnr
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
    private val requestLatency = Summary.build().name("simba_altinn_tilgangskontroll_latency_seconds").help("altinn tilgangskontroll latency in seconds").register()

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandKey(Key.EVENT_NAME.str)
                it.demandValues(
                    Key.BEHOV to BehovType.TILGANGSKONTROLL.name
                )
                it.requireKeys(
                    Key.UUID,
                    DataFelt.ORGNRUNDERENHET,
                    DataFelt.FNR
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val json = packet.toJson().parseJson()

        logger.info("Mottok melding med behov '${BehovType.TILGANGSKONTROLL}'.")
        sikkerLogger.info("Mottok melding:\n${json.toPretty()}")

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.behov(BehovType.TILGANGSKONTROLL)
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
                    hentTilgang(it, context)
                }
                    .onFailure { feil ->
                        context.publishFeil("Ukjent feil.", feil, it)
                    }
            }
        }
    }

    private fun hentTilgang(melding: Melding, context: MessageContext) {
        val requestTimer = requestLatency.startTimer()
        runCatching {
            runBlocking {
                altinnClient.harRettighetForOrganisasjon(melding.fnr.verdi, melding.orgnr.verdi)
            }
        }.also {
            requestTimer.observeDuration()
        }
            .onSuccess { harTilgang ->
                val tilgang = if (harTilgang) Tilgang.HAR_TILGANG else Tilgang.IKKE_TILGANG
                context.publishSuksess(tilgang, melding)
            }
            .onFailure {
                context.publishFeil("Feil ved henting av rettigheter fra Altinn.", it, melding)
            }
    }

    private fun MessageContext.publishSuksess(tilgang: Tilgang, melding: Melding) {
        "Tilgang hentet: '$tilgang'.".also {
            logger.info(it)
            sikkerLogger.info("$it orgnr='${melding.orgnr}' fnr='${melding.fnr}'")
        }

        publish(
            Key.EVENT_NAME to melding.event.toJson(),
            Key.DATA to "".toJson(),
            Key.UUID to melding.transaksjonId.toJson(),
            DataFelt.TILGANG to tilgang.toJson(Tilgang.serializer())
        )
            .also {
                logger.info("Publiserte data for '${BehovType.TILGANGSKONTROLL}'.")
                sikkerLogger.info("Publiserte data:\n${it.toPretty()}")
            }
    }

    private fun MessageContext.publishFeil(feilmelding: String, feil: Throwable, melding: Melding?) {
        logger.error("$feilmelding Se sikker logg for mer info.")
        sikkerLogger.error(feilmelding, feil)

        Fail(
            eventName = melding?.event,
            behov = BehovType.TILGANGSKONTROLL,
            feilmelding = feilmelding,
            forespørselId = null,
            uuid = melding?.transaksjonId?.toString()
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

private data class Melding(
    val event: EventName,
    val transaksjonId: UUID,
    val orgnr: Orgnr,
    val fnr: Fnr
) {
    companion object {
        fun fra(json: JsonElement): Melding =
            json.toMap().let {
                Melding(
                    event = Key.EVENT_NAME.les(EventName.serializer(), it),
                    transaksjonId = Key.UUID.les(UuidSerializer, it),
                    orgnr = DataFelt.ORGNRUNDERENHET.les(String.serializer(), it).let(::Orgnr),
                    fnr = DataFelt.FNR.les(String.serializer(), it).let(::Fnr)
                )
            }
    }

    fun withLogFields(block: (Melding) -> Unit) {
        MdcUtils.withLogFields(
            Log.event(event),
            Log.transaksjonId(transaksjonId)
        ) {
            block(this)
        }
    }
}
