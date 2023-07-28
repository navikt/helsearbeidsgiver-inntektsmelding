@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Periode
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.createFail
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toPretty
import no.nav.helsearbeidsgiver.felles.valueNullable
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.inntekt.InntektskomponentResponse
import no.nav.helsearbeidsgiver.inntekt.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.log.logger
import java.time.LocalDate
import kotlin.system.measureTimeMillis

/**
 * @return en periode tre måneder tilbake fra nyeste sammenhengende
 * sykmeldingsperiode
 */
fun finnInntektPeriode(sykmeldinger: List<Periode>): Periode {
    val skjæringstidspunkt = finnSkjæringstidspunkt(sykmeldinger)
    val skjæringstidspunktMåned = skjæringstidspunkt.withDayOfMonth(1)
    return Periode(
        skjæringstidspunktMåned.minusMonths(3),
        skjæringstidspunktMåned.minusDays(1)
    )
}

/**
 * Sorter fom fra minst til størst,
 * returnerer den siste sammenhengende perioden i lista,
 * eller det siste elementet om ingen hører sammen
 */
fun finnSkjæringstidspunkt(sykmeldinger: List<Periode>): LocalDate =
    if (sykmeldinger.size <= 1) {
        sykmeldinger.first()
    } else {
        sykmeldinger.sortedBy { it.fom }
            .reduce { p1, p2 ->
                p1.slåSammenIgnorerHelgOrNull(p2)
                    ?: p2
            }
    }
        .fom

class InntektLøser(
    rapidsConnection: RapidsConnection,
    private val inntektKlient: InntektKlient
) : Løser(rapidsConnection) {

    private val logger = logger()
    private val INNTEKT = BehovType.INNTEKT

    override fun accept(): River.PacketValidation = River.PacketValidation {
        it.demandValues(Key.BEHOV to INNTEKT.name)
        it.requireKeys(DataFelt.TRENGER_INNTEKT)
        it.interestedIn(DataFelt.SKJAERINGSTIDSPUNKT)
        it.rejectKey(Key.LØSNING.str)
    }

    private fun hentInntekt(fnr: String, periode: Periode, callId: String): InntektskomponentResponse =
        runBlocking {
            sikkerLogger.info("Henter inntekt for $fnr i perioden ${periode.fom} til ${periode.tom} (callId: $callId)")
            val response: InntektskomponentResponse
            measureTimeMillis {
                response = inntektKlient.hentInntektListe(fnr, callId, "helsearbeidsgiver-im-inntekt", periode.fom, periode.tom, "8-28", "Sykepenger")
            }.also {
                logger.info("Inntekt endepunkt took $it")
            }
            response
        }

    override fun onBehov(packet: JsonMessage) {
        measureTimeMillis {
            logger.info("Mottar pakke")
            sikkerLogger.info("Mottar pakke:\n${packet.toPretty()}")
            val uuid = packet[Key.UUID.str].asText()
            logger.info("Løser behov $INNTEKT med uuid $uuid")
            val trengerInntekt = packet[DataFelt.TRENGER_INNTEKT.str].toJsonElement().fromJson(TrengerInntekt.serializer())

            val fnr = trengerInntekt.fnr
            val orgnr = trengerInntekt.orgnr
            val nyInntektDato = packet.valueNullable(DataFelt.SKJAERINGSTIDSPUNKT)
                ?.toJsonElement()
                ?.fromJson(LocalDateSerializer)
            val sykPeriode = bestemPeriode(nyInntektDato, trengerInntekt.sykmeldingsperioder, trengerInntekt.egenmeldingsperioder)
            if (sykPeriode.isEmpty()) {
                logger.error("Sykmeldingsperiode mangler for uuid $uuid")
                publishFail(packet.createFail("Mangler sykmeldingsperiode", behovType = BehovType.INNTEKT))
                return
            }
            try {
                val inntektPeriode = finnInntektPeriode(sykPeriode)
                sikkerLogger.info("Skal finne inntekt for $fnr orgnr $orgnr i perioden: ${inntektPeriode.fom} - ${inntektPeriode.tom}")
                val inntektResponse = hentInntekt(fnr, inntektPeriode, "helsearbeidsgiver-im-inntekt-$uuid")
                sikkerLogger.info("Fant inntektResponse: $inntektResponse")
                val inntekt = mapInntekt(inntektResponse, orgnr)
                publishData(
                    JsonMessage.newMessage(
                        mapOf(
                            Key.UUID.str to uuid,
                            Key.DATA.str to "",
                            DataFelt.INNTEKT.str to inntekt
                        )
                    )
                )
                sikkerLogger.info("Fant inntekt $inntekt for $fnr og orgnr $orgnr")
            } catch (ex: Exception) {
                sikkerLogger.error("Feil ved henting av inntekt for $fnr!", ex)
                publishFail(packet.createFail("Klarte ikke hente inntekt", behovType = BehovType.INNTEKT))
            }
        }.also {
            logger.info("Inntekt Løser took $it")
        }
    }

    private fun bestemPeriode(dato: LocalDate?, sykmeldingPerioder: List<Periode>?, egenmeldingPerioder: List<Periode>?): List<Periode> {
        if (dato == null) {
            logger.debug("Bruker sykmeldingsperiode fra spleis-forespørsel")
            return egenmeldingPerioder.orEmpty() + sykmeldingPerioder.orEmpty()
        }
        logger.debug("Bruker innsendt dato $dato fra bruker")
        return listOf(Periode(dato, dato))
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {}
}
