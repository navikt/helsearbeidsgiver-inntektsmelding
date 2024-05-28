package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import io.prometheus.client.Summary
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.InntektPerMaaned
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
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
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
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
                hentInntekt(behov)
            }
                .onFailure { feil ->
                    sikkerLogger.error("Ukjent feil.", feil)
                    behov.createFail("Ukjent feil.").also { this.publishFail(it) }
                }
        }
    }

    private fun hentInntekt(behov: Behov) {
        val json = behov.jsonMessage.toJson().parseJson().toMap()

        val transaksjonId = Key.UUID.les(UuidSerializer, json)
        val fnr = Key.FNR.les(Fnr.serializer(), json)
        val orgnr = Key.ORGNRUNDERENHET.les(Orgnr.serializer(), json)
        val skjaeringstidspunkt = Key.SKJAERINGSTIDSPUNKT.les(LocalDateSerializer, json)

        val requestTimer = requestLatency.startTimer()
        val fom = skjaeringstidspunkt.minusMaaneder(3)
        val middle = skjaeringstidspunkt.minusMaaneder(2)
        val tom = skjaeringstidspunkt.minusMaaneder(1)

        hentInntektPerOrgnrOgMaaned(fnr, fom, tom, transaksjonId)
            .onSuccess { inntektPerOrgnrOgMaaned ->
                val inntektPerMaaned = inntektPerOrgnrOgMaaned[orgnr.verdi]
                    .orEmpty()

                val inntekt = listOf(fom, middle, tom)
                    .associateWith { inntektPerMaaned[it] }
                    .map { (maaned, inntekt) -> InntektPerMaaned(maaned, inntekt) }
                    .let(::Inntekt)

                rapidsConnection.publishData(
                    eventName = behov.event,
                    transaksjonId = transaksjonId,
                    forespoerselId = behov.forespoerselId?.let(UUID::fromString),
                    Key.INNTEKT to inntekt.toJson(Inntekt.serializer())
                )
            }
            .onFailure {
                sikkerLogger.error("Klarte ikke hente inntekt.", it)
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
                    navConsumerId = "im-inntekt",
                    callId = callId
                )
            }
        }
    }
}

private fun LocalDate.minusMaaneder(maanederTilbake: Long): YearMonth =
    toYearMonth().minusMonths(maanederTilbake)
