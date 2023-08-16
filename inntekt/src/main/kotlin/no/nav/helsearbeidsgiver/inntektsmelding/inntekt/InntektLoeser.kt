package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import io.prometheus.client.Summary
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
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
import no.nav.helsearbeidsgiver.felles.Fnr
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.InntektPerMaaned
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Orgnr
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.rejectKeys
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.felles.utils.toYearMonth
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class InntektLoeser(
    private val rapid: RapidsConnection,
    private val inntektKlient: InntektKlient
) : Løser(rapid) {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()
    private val requestLatency = Summary.build()
        .name("simba_inntekt_latency_seconds")
        .help("hentInntekt latency in seconds")
        .register()

    override fun accept(): River.PacketValidation =
        River.PacketValidation {
            it.demandKey(Key.EVENT_NAME.str)
            it.demandValues(
                Key.BEHOV to BehovType.INNTEKT.name
            )
            it.requireKeys(
                Key.UUID,
                DataFelt.ORGNRUNDERENHET,
                DataFelt.FNR,
                DataFelt.SKJAERINGSTIDSPUNKT
            )
            it.rejectKeys(Key.LØSNING)
        }

    override fun onBehov(packet: JsonMessage) {
        val json = packet.toJson().parseJson()

        logger.info("Mottok melding med behov '${BehovType.INNTEKT}'.")
        sikkerLogger.info("Mottok melding:\n${json.toPretty()}")

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.behov(BehovType.INNTEKT)
        ) {
            val melding = runCatching {
                Melding.fra(json)
            }
                .onFailure {
                    publishFeil("Klarte ikke lese påkrevde felt fra innkommende melding.", it, null)
                }
                .getOrNull()

            melding?.withLogFields {
                runCatching {
                    hentInntekt(it)
                }
                    .onFailure { feil ->
                        publishFeil("Ukjent feil.", feil, it)
                    }
            }
        }
    }

    private fun hentInntekt(melding: Melding) {
        hentInntektPerOrgnrOgMaaned(melding.fnr, melding.skjaeringstidspunkt, melding.transaksjonId)
            .onSuccess {
                val inntekt = it[melding.orgnr.verdi]
                    .orEmpty()
                    .map { (maaned, inntekt) -> InntektPerMaaned(maaned, inntekt) }
                    .let(::Inntekt)

                publishSuksess(inntekt, melding)
            }
            .onFailure {
                publishFeil("Klarte ikke hente inntekt.", it, melding)
            }
    }

    private fun hentInntektPerOrgnrOgMaaned(fnr: Fnr, skjaeringstidspunkt: LocalDate, id: UUID): Result<Map<String, Map<YearMonth, Double>>> {
        val fom = skjaeringstidspunkt.minusMaaneder(3)
        val tom = skjaeringstidspunkt.minusMaaneder(1)

        val callId = "helsearbeidsgiver-im-inntekt-$id"

        sikkerLogger.info("Henter inntekt for $fnr i perioden $fom til $tom (callId: $callId).")
        val requestTimer = requestLatency.startTimer()
        return runCatching {
            runBlocking {
                inntektKlient.hentInntektPerOrgnrOgMaaned(
                    fnr = fnr.verdi,
                    fom = fom,
                    tom = tom,
                    navConsumerId = "helsearbeidsgiver-im-inntekt",
                    callId = callId
                )
            }.also {
                requestTimer.observeDuration()
            }
        }
    }

    private fun publishSuksess(inntekt: Inntekt, melding: Melding) {
        sikkerLogger.info("Hentet inntekt for ${melding.fnr}:\n$inntekt")

        rapid.publish(
            Key.EVENT_NAME to melding.event.toJson(),
            Key.DATA to "".toJson(),
            Key.UUID to melding.transaksjonId.toJson(),
            DataFelt.INNTEKT to inntekt.toJson(Inntekt.serializer())
        )
            .also {
                logger.info("Publiserte data for '${BehovType.INNTEKT}'.")
                sikkerLogger.info("Publiserte data:\n${it.toPretty()}")
            }
    }

    private fun publishFeil(feilmelding: String, feil: Throwable, melding: Melding?) {
        logger.error("$feilmelding Se sikker logg for mer info.")
        sikkerLogger.error(feilmelding, feil)

        Fail(
            eventName = melding?.event,
            behov = BehovType.INNTEKT,
            feilmelding = feilmelding,
            forespørselId = null,
            uuid = melding?.transaksjonId?.toString()
        )
            .toJsonMessage()
            .toJson()
            .also(rapid::publish)
            .also {
                logger.error("Publiserte feil for ${BehovType.INNTEKT}.")
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

private fun LocalDate.minusMaaneder(maanederTilbake: Long): YearMonth =
    toYearMonth().minusMonths(maanederTilbake)

private data class Melding(
    val event: EventName,
    val transaksjonId: UUID,
    val orgnr: Orgnr,
    val fnr: Fnr,
    val skjaeringstidspunkt: LocalDate
) {
    companion object {
        fun fra(json: JsonElement): Melding =
            json.toMap().let {
                Melding(
                    event = Key.EVENT_NAME.les(EventName.serializer(), it),
                    transaksjonId = Key.UUID.les(UuidSerializer, it),
                    orgnr = DataFelt.ORGNRUNDERENHET.les(String.serializer(), it).let(::Orgnr),
                    fnr = DataFelt.FNR.les(String.serializer(), it).let(::Fnr),
                    skjaeringstidspunkt = DataFelt.SKJAERINGSTIDSPUNKT.les(LocalDateSerializer, it)
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
