package no.nav.helsearbeidsgiver.inntektsmelding.db

import no.nav.helsearbeidsgiver.domene.inntektsmelding.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Inntektsmelding
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import kotlin.reflect.full.memberProperties

fun mapInntektsmelding(
    request: Innsending,
    fulltnavnArbeidstaker: String,
    arbeidsgiver: String,
    innsenderNavn: String
): Inntektsmelding =
    try {
        Inntektsmelding(
            orgnrUnderenhet = request.orgnrUnderenhet,
            identitetsnummer = request.identitetsnummer,
            fulltNavn = fulltnavnArbeidstaker,
            virksomhetNavn = arbeidsgiver,
            behandlingsdager = request.behandlingsdager,
            egenmeldingsperioder = request.egenmeldingsperioder,
            bestemmendeFraværsdag = request.bestemmendeFraværsdag,
            fraværsperioder = request.fraværsperioder,
            arbeidsgiverperioder = request.arbeidsgiverperioder,
            beregnetInntekt = request.inntekt.beregnetInntekt,
            inntekt = request.inntekt,
            fullLønnIArbeidsgiverPerioden = request.fullLønnIArbeidsgiverPerioden,
            refusjon = request.refusjon,
            naturalytelser = request.naturalytelser,
            tidspunkt = ZonedDateTime.now().toOffsetDateTime(),
            årsakInnsending = request.årsakInnsending,
            innsenderNavn = innsenderNavn,
            forespurtData = request.forespurtData,
            telefonnummer = request.telefonnummer
        )
    } catch (ex: Exception) {
        throw UgyldigFormatException(ex)
    }

class UgyldigFormatException(ex: Exception) : Exception(ex)

fun Inntektsmelding.erLik(im: Inntektsmelding): Result<Boolean> {
    val mL = mutableListOf<Exception>()
    this::class.memberProperties.forEach() { prop ->
        val thisValue = prop.getter.call(this)
        val imValue = prop.getter.call(im)
        if (thisValue !is OffsetDateTime && thisValue != imValue) {
            mL.add(Exception("Inntektsmelding er ulik for property ${prop.name}:\n$thisValue\n$imValue"))
        }
    }
    return if (mL.isEmpty()) Result.success(true) else Result.failure(mL.first())
}
