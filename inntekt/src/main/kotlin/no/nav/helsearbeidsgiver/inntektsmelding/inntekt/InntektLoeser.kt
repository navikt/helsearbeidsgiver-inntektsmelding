package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import io.prometheus.client.Summary
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Fnr
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.InntektPerMaaned
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Orgnr
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.rejectKeys
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toPretty
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.felles.utils.toYearMonth
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class InntektLoeser(
    private val rapid: RapidsConnection,
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
                DataFelt.ORGNRUNDERENHET,
                DataFelt.FNR,
                DataFelt.SKJAERINGSTIDSPUNKT
            )
            it.rejectKeys(Key.LØSNING)
        }

    override fun onBehov(behov: Behov) {
        logger.info("Mottok melding med behov '${BehovType.INNTEKT}'.")
        sikkerLogger.info("Mottok melding:\n${behov.toJsonMessage().toPretty()}")

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
            .onSuccess {
                val inntekt = it[behov.orgnr().verdi]
                    .orEmpty()
                    .map { (maaned, inntekt) -> InntektPerMaaned(maaned, inntekt) }
                val mndISvar = inntekt.associate { it.maaned to it.inntekt }
                val alleMnd = listOf(fom, middle, tom).associate { mnd -> mnd to mndISvar[mnd] }
                    .map { (maaned, inntekt) -> InntektPerMaaned(maaned, inntekt) }.let(::Inntekt)
                publishData(
                    behov.createData(
                        mapOf(
                            DataFelt.INNTEKT to alleMnd
                        )
                    )
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

private fun Behov.skjaeringstidspunkt(): LocalDate = LocalDate.parse(this[DataFelt.SKJAERINGSTIDSPUNKT].asText())
private fun Behov.fnr(): Fnr = Fnr(this[DataFelt.FNR].asText())
private fun Behov.orgnr(): Orgnr = Orgnr(this[DataFelt.ORGNRUNDERENHET].asText())

private fun Behov.validate() {
    this.skjaeringstidspunkt()
    this.fnr()
    this.orgnr()
}
