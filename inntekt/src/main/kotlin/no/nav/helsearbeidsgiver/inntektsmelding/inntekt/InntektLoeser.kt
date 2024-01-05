package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import io.prometheus.client.Summary
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Fnr
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.InntektPerMaaned
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Orgnr
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.publishData
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toPretty
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.felles.utils.toYearMonth
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class InntektLoeser(
    rapid: RapidsConnection,
    private val inntektKlient: InntektKlient
) : Loeser(rapid) {
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
            it.interestedIn(
                Key.UUID,
                Key.ORGNRUNDERENHET,
                Key.FNR,
                Key.SKJAERINGSTIDSPUNKT,
                Key.FORESPOERSEL_ID
            )
        }

    override fun onBehov(behov: Behov) {
        logger.info("Mottok melding med behov '${BehovType.INNTEKT}'.")
        sikkerLogger.info("Mottok melding:\n${behov.jsonMessage.toPretty()}")

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(behov.event),
            Log.behov(BehovType.INNTEKT)
        ) {
            runCatching {
                behov.validate()
            }.onFailure {
                behov.createFail("Klarte ikke lese påkrevde felt fra innkommende melding.").also { publishFail(it) }
            }

            runCatching {
                hentInntekt(behov)
            }
                .onFailure { feil ->
                    sikkerLogger.error("Ukjent feil.", feil)
                    behov.createFail("Ukjent feil.").also { this.publishFail(it) }
                }
        }
    }

    private fun hentInntekt(behov: Behov) {
        val requestTimer = requestLatency.startTimer()
        val fom = behov.skjaeringstidspunkt().minusMaaneder(3)
        val middle = behov.skjaeringstidspunkt().minusMaaneder(2)
        val tom = behov.skjaeringstidspunkt().minusMaaneder(1)

        hentInntektPerOrgnrOgMaaned(behov.fnr(), fom, tom, UUID.fromString(behov.uuid()))
            .onSuccess { inntektPerOrgnrOgMaaned ->
                val inntektPerMaaned = inntektPerOrgnrOgMaaned[behov.orgnr().verdi]
                    .orEmpty()

                val inntekt = listOf(fom, middle, tom)
                    .associateWith { inntektPerMaaned[it] }
                    .map { (maaned, inntekt) -> InntektPerMaaned(maaned, inntekt) }
                    .let(::Inntekt)

                val json = behov.jsonMessage.toJson().parseJson().toMap()

                val transaksjonId = Key.UUID.lesOrNull(UuidSerializer, json)

                rapidsConnection.publishData(
                    eventName = behov.event,
                    transaksjonId = transaksjonId,
                    forespoerselId = behov.forespoerselId?.let(UUID::fromString),
                    Key.INNTEKT to inntekt.toJson(Inntekt.serializer())
                )
            }
            .onFailure {
                publishFail(behov.createFail("Klarte ikke hente inntekt."))
            }
        requestTimer.observeDuration()
    }

    private fun hentInntektPerOrgnrOgMaaned(fnr: Fnr, fom: YearMonth, tom: YearMonth, id: UUID): Result<Map<String, Map<YearMonth, Double>>> {
        val callId = "helsearbeidsgiver-im-inntekt-$id"

        sikkerLogger.info("Henter inntekt for $fnr i perioden $fom til $tom (callId: $callId).")

        return runCatching {
            runBlocking {
                inntektKlient.hentInntektPerOrgnrOgMaaned(
                    fnr = fnr.verdi,
                    fom = fom,
                    tom = tom,
                    navConsumerId = "helsearbeidsgiver-im-inntekt",
                    callId = callId
                )
            }
        }
    }
}

private fun LocalDate.minusMaaneder(maanederTilbake: Long): YearMonth =
    toYearMonth().minusMonths(maanederTilbake)

private fun Behov.skjaeringstidspunkt(): LocalDate = LocalDate.parse(jsonMessage[Key.SKJAERINGSTIDSPUNKT.toString()].asText())
private fun Behov.fnr(): Fnr = Fnr(jsonMessage[Key.FNR.toString()].asText())
private fun Behov.orgnr(): Orgnr = Orgnr(jsonMessage[Key.ORGNRUNDERENHET.toString()].asText())

private fun Behov.validate() {
    this.skjaeringstidspunkt()
    this.fnr()
    this.orgnr()
}
