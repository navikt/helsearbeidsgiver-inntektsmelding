@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import kotlinx.coroutines.runBlocking
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
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.inntekt.InntektskomponentResponse
import org.slf4j.LoggerFactory
import java.time.LocalDate

/**
 * @return en periode tre måneder tilbake fra nyeste sammenhengende
 * sykmeldingsperiode
 */
fun finnInntektPeriode(sykmeldinger: List<Periode>): Periode {
    val bestemmende = finnBestemmendeFraværsdag(sykmeldinger)
    val bestemmendeMaaned = bestemmende.withDayOfMonth(1)
    return Periode(bestemmendeMaaned.minusMonths(3), bestemmendeMaaned.minusDays(1))
}

/**
 * Sorter fom fra minst til størst,
 * returnerer den siste sammenhengende perioden i lista,
 * eller det siste elementet om ingen hører sammen
 */
fun finnBestemmendeFraværsdag(sykmeldinger: List<Periode>): LocalDate =
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
    private val BEHOV = BehovType.INNTEKT

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAll(Key.BEHOV.str, BEHOV)
                it.requireKey(Key.ID.str, Key.IDENTITETSNUMMER.str, Key.ORGNRUNDERENHET.str, Key.SESSION.str)
                it.rejectKey(Key.LØSNING.str)
            }
        }.register(this)
    }

    private fun hentInntekt(fnr: String, fra: LocalDate, til: LocalDate, callId: String): InntektskomponentResponse =
        runBlocking {
            sikkerlogg.info("Henter inntekt for $fnr i perioden $fra til $til (callId: $callId)")
            inntektKlient.hentInntektListe(fnr, callId, "helsearbeidsgiver-im-inntekt", fra, til, "8-28", "Sykepenger")
        }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        logger.info("Mottar pakke")
        sikkerlogg.info("Mottar pakke: ${packet.toJson()}")
        val uuid = packet[Key.ID.str].asText()
        logger.info("Løser behov $BEHOV med id $uuid")
        sikkerlogg.info("Løser behov $BEHOV med id $uuid")
        val fnr = packet[Key.IDENTITETSNUMMER.str].asText()
        val orgnr = packet.value(Key.ORGNRUNDERENHET).asText()
        val imLøsning = packet.value(Key.SESSION)[BehovType.HENT_TRENGER_IM.name]?.toJsonElement()?.fromJson(HentTrengerImLøsning.serializer())
        val sykPeriode = imLøsning?.value?.sykmeldingsperioder
        if (sykPeriode.isNullOrEmpty()) {
            logger.warn("Sykmeldingsperiode mangler for uuid $uuid")
            packet.setLøsning(BEHOV, InntektLøsning(error = Feilmelding("Mangler sykmeldingsperiode")))
            context.publish(packet.toJson())
            return
        }
        try {
            val periode = finnInntektPeriode(sykPeriode)
            sikkerlogg.info("Skal finne inntekt for $fnr orgnr $orgnr i perioden: ${periode.fom} - ${periode.tom}")
            val inntektResponse = hentInntekt(fnr, periode.fom, periode.tom, "helsearbeidsgiver-im-inntekt-$uuid")
            sikkerlogg.info("Fant inntektResponse: $inntektResponse")
            val inntekt = mapInntekt(inntektResponse, orgnr)
            packet.setLøsning(BEHOV, InntektLøsning(inntekt))
            context.publish(packet.toJson())
            sikkerlogg.info("Fant inntekt $inntekt for $fnr og orgnr $orgnr")
        } catch (ex: Exception) {
            logger.error("Feil!", ex)
            packet.setLøsning(BEHOV, InntektLøsning(error = Feilmelding("Klarte ikke hente inntekt")))
            sikkerlogg.info("Det oppstod en feil ved henting av inntekt for $fnr", ex)
            context.publish(packet.toJson())
        }
    }

    private fun JsonMessage.setLøsning(nøkkel: BehovType, data: Any) {
        this[Key.LØSNING.str] = mapOf(
            nøkkel.name to data
        )
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {}
}
