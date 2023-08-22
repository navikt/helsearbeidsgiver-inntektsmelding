package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fnr
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.InntektPerMaaned
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Orgnr
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.rejectKeys
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toPretty
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.felles.utils.toYearMonth
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
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

    override fun onBehov(packet: JsonMessage) {
    }

    private fun hentInntekt(behov: Behov) {
        hentInntektPerOrgnrOgMaaned(behov.fnr(), behov.skjaeringstidspunkt(), UUID.fromString(behov.uuid()))
            .onSuccess {
                val inntekt = it[behov.orgnr().verdi]
                    .orEmpty()
                    .map { (maaned, inntekt) -> InntektPerMaaned(maaned, inntekt) }
                    .let(::Inntekt)

                publishData(
                    behov.createData(
                        mapOf(
                            DataFelt.INNTEKT to inntekt
                        )
                    )
                )
            }
            .onFailure {
                publishFail(behov.createFail("Klarte ikke hente inntekt."))
            }
    }

    private fun hentInntektPerOrgnrOgMaaned(fnr: Fnr, skjaeringstidspunkt: LocalDate, id: UUID): Result<Map<String, Map<YearMonth, Double>>> {
        val fom = skjaeringstidspunkt.minusMaaneder(3)
        val tom = skjaeringstidspunkt.minusMaaneder(1)

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
}
