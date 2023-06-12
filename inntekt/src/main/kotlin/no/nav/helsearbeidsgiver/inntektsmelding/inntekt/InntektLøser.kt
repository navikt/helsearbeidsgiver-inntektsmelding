@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Feilmelding
import no.nav.helsearbeidsgiver.felles.HentTrengerImLøsning
import no.nav.helsearbeidsgiver.felles.InntektLøsning
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Periode
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
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

class InntektLøser(rapidsConnection: RapidsConnection, val inntektKlient: InntektKlient) : River.PacketListener {

    private val logger = logger()
    private val INNTEKT = BehovType.INNTEKT

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll(Key.BEHOV.str, INNTEKT)
                it.requireKey(Key.ID.str, Key.SESSION.str)
                it.interestedIn(Key.BOOMERANG.str) // TODO: forsøk å heller splitte opp i to løsere
                it.rejectKey(Key.LØSNING.str)
            }
        }.register(this)
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

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        measureTimeMillis {
            logger.info("Mottar pakke")
            sikkerLogger.info("Mottar pakke: ${packet.toJson()}")
            val uuid = packet[Key.ID.str].asText()
            logger.info("Løser behov $INNTEKT med id $uuid")
            val imLøsning = hentSpleisDataFraSession(packet)
            val fnr = imLøsning.value!!.fnr
            val orgnr = imLøsning.value!!.orgnr
            val nyInntektDato = packet.valueNullable(Key.BOOMERANG)
                ?.toJsonElement()
                ?.fromJson(MapSerializer(Key.serializer(), JsonElement.serializer()))
                ?.get(Key.INNTEKT_DATO)
                ?.fromJson(LocalDateSerializer)
            val sykPeriode = bestemPeriode(nyInntektDato, imLøsning.value?.sykmeldingsperioder, imLøsning.value?.egenmeldingsperioder)
            if (sykPeriode.isEmpty()) {
                logger.error("Sykmeldingsperiode mangler for uuid $uuid")
                packet.setLøsning(INNTEKT, InntektLøsning(error = Feilmelding("Mangler sykmeldingsperiode")))
                context.publish(packet.toJson())
                return
            }
            try {
                val inntektPeriode = finnInntektPeriode(sykPeriode)
                sikkerLogger.info("Skal finne inntekt for $fnr orgnr $orgnr i perioden: ${inntektPeriode.fom} - ${inntektPeriode.tom}")
                val inntektResponse = hentInntekt(fnr, inntektPeriode, "helsearbeidsgiver-im-inntekt-$uuid")
                sikkerLogger.info("Fant inntektResponse: $inntektResponse")
                val inntekt = mapInntekt(inntektResponse, orgnr)
                packet.setLøsning(INNTEKT, InntektLøsning(inntekt))
                context.publish(packet.toJson())
                sikkerLogger.info("Fant inntekt $inntekt for $fnr og orgnr $orgnr")
            } catch (ex: Exception) {
                sikkerLogger.error("Feil ved henting av inntekt for $fnr!", ex)
                packet.setLøsning(INNTEKT, InntektLøsning(error = Feilmelding("Klarte ikke hente inntekt")))
                context.publish(packet.toJson())
            }
        }.also {
            logger.info("Inntekt Løser took $it")
        }
    }

    private fun hentSpleisDataFraSession(packet: JsonMessage): HentTrengerImLøsning =
        try {
            packet[Key.SESSION.str][BehovType.HENT_TRENGER_IM.name]
                .toJsonElement()
                .fromJson(HentTrengerImLøsning.serializer())
        } catch (ex: Exception) {
            HentTrengerImLøsning(error = Feilmelding("Klarte ikke hente ut spleisdata fra ${Key.SESSION},  ${BehovType.HENT_TRENGER_IM}"))
        }

    private fun bestemPeriode(dato: LocalDate?, sykmeldingPerioder: List<Periode>?, egenmeldingPerioder: List<Periode>?): List<Periode> {
        if (dato == null) {
            logger.debug("Bruker sykmeldingsperiode fra spleis-forespørsel")
            return egenmeldingPerioder.orEmpty() + sykmeldingPerioder.orEmpty()
        }
        logger.debug("Bruker innsendt dato $dato fra bruker")
        return listOf(Periode(dato, dato))
    }

    private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
        this[Key.LØSNING.str] = mapOf(
            nøkkel.name to data
        )
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {}
}
