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
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import no.nav.helsearbeidsgiver.felles.value
import no.nav.helsearbeidsgiver.felles.valueNullable
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.inntekt.InntektskomponentResponse
import no.nav.helsearbeidsgiver.inntekt.LocalDateSerializer
import org.slf4j.LoggerFactory
import java.time.LocalDate

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
                p1.slåSammenOrNull(p2)
                    ?: p2
            }
    }
        .fom

class InntektLøser(rapidsConnection: RapidsConnection, val inntektKlient: InntektKlient) : River.PacketListener {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val INNTEKT = BehovType.INNTEKT

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll(Key.BEHOV.str, INNTEKT)
                it.requireKey(Key.ID.str, Key.IDENTITETSNUMMER.str, Key.ORGNRUNDERENHET.str)
                it.interestedIn(Key.SESSION.str, Key.BOOMERANG.str) // TODO: forsøk å heller splitte opp i to løsere
                it.rejectKey(Key.LØSNING.str)
            }
        }.register(this)
    }

    private fun hentInntekt(fnr: String, periode: Periode, callId: String): InntektskomponentResponse =
        runBlocking {
            sikkerlogg.info("Henter inntekt for $fnr i perioden ${periode.fom} til ${periode.tom} (callId: $callId)")
            inntektKlient.hentInntektListe(fnr, callId, "helsearbeidsgiver-im-inntekt", periode.fom, periode.tom, "8-28", "Sykepenger")
        }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Mottar pakke")
        sikkerlogg.info("Mottar pakke: ${packet.toJson()}")
        val uuid = packet[Key.ID.str].asText()
        logger.info("Løser behov $INNTEKT med id $uuid")
        sikkerlogg.info("Løser behov $INNTEKT med id $uuid")
        val fnr = packet[Key.IDENTITETSNUMMER.str].asText()
        val orgnr = packet.value(Key.ORGNRUNDERENHET).asText()
        val imLøsning = packet.value(Key.SESSION)[BehovType.HENT_TRENGER_IM.name]
            ?.toJsonElement()
            ?.fromJson(HentTrengerImLøsning.serializer())
        val sykPeriode = imLøsning?.value?.sykmeldingsperioder ?: lagPeriode(
            packet.valueNullable(Key.BOOMERANG)
                ?.toJsonElement()
                ?.fromJson(MapSerializer(Key.serializer(), JsonElement.serializer()))
                ?.get(Key.INNTEKT_DATO)
                ?.fromJson(LocalDateSerializer)
        )
        if (sykPeriode.isEmpty()) {
            logger.error("Sykmeldingsperiode mangler for uuid $uuid")
            packet.setLøsning(INNTEKT, InntektLøsning(error = Feilmelding("Mangler sykmeldingsperiode")))
            context.publish(packet.toJson())
            return
        }
        try {
            val inntektPeriode = finnInntektPeriode(sykPeriode)
            sikkerlogg.info("Skal finne inntekt for $fnr orgnr $orgnr i perioden: ${inntektPeriode.fom} - ${inntektPeriode.tom}")
            val inntektResponse = hentInntekt(fnr, inntektPeriode, "helsearbeidsgiver-im-inntekt-$uuid")
            sikkerlogg.info("Fant inntektResponse: $inntektResponse")
            val inntekt = mapInntekt(inntektResponse, orgnr)
            packet.setLøsning(INNTEKT, InntektLøsning(inntekt))
            context.publish(packet.toJson())
            sikkerlogg.info("Fant inntekt $inntekt for $fnr og orgnr $orgnr")
        } catch (ex: Exception) {
            logger.error("Feil!", ex)
            packet.setLøsning(INNTEKT, InntektLøsning(error = Feilmelding("Klarte ikke hente inntekt")))
            sikkerlogg.info("Det oppstod en feil ved henting av inntekt for $fnr", ex)
            context.publish(packet.toJson())
        }
    }

    private fun lagPeriode(dato: LocalDate?): List<Periode> {
        if (dato == null) {
            logger.error("Ugyldig dato-verdi i dato")
            return emptyList()
        }
        return listOf(Periode(dato, dato))
    }

    private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
        this[Key.LØSNING.str] = mapOf(
            nøkkel.name to data
        )
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {}
}
