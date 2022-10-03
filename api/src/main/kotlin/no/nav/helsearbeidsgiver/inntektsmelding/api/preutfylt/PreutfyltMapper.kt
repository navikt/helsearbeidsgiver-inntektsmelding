@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.preutfylt

import io.ktor.http.HttpStatusCode
import no.nav.helsearbeidsgiver.felles.Behov
import no.nav.helsearbeidsgiver.felles.Løsning
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.inntektsmelding.api.dto.Inntekt
import no.nav.helsearbeidsgiver.inntektsmelding.api.dto.MottattArbeidsforhold
import no.nav.helsearbeidsgiver.inntektsmelding.api.dto.MottattPeriode
import no.nav.helsearbeidsgiver.inntektsmelding.api.dto.PreutfyltResponse
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.FeilmeldingConstraint
import org.valiktor.ConstraintViolation
import org.valiktor.ConstraintViolationException
import org.valiktor.DefaultConstraintViolation
import java.time.LocalDate

class PreutfyltMapper(val uuid: String, var resultat: Resultat, val request: PreutfyllRequest) {

    fun hasErrors(): Boolean {
        return resultat.løsninger.any { it.error != null }
    }

    fun getConstraintViolations(): List<ConstraintViolation> {
        return resultat.løsninger
            .filter { it.error != null && !it.error!!.melding.isBlank() }
            .map { mapConstraint(it) }
    }

    fun mapConstraint(løsning: Løsning): ConstraintViolation {
        val behov = løsning.behov
        if (behov.equals(Behov.VIRKSOMHET.name)) {
            return DefaultConstraintViolation("orgnrUnderenhet", løsning.error!!.melding, FeilmeldingConstraint())
        }
        if (behov.equals(Behov.FULLT_NAVN.name)) {
            return DefaultConstraintViolation("identitetsnummer", løsning.error!!.melding, FeilmeldingConstraint())
        }
        return DefaultConstraintViolation("ukjent", løsning.error!!.melding, FeilmeldingConstraint())
    }

    fun findLøsningByBehov(behov: Behov): Løsning {
        return resultat.løsninger.first { it.behov.equals(behov.name) }
    }

    fun getResponse(): PreutfyltResponse {
        if (hasErrors()) {
            throw ConstraintViolationException(getConstraintViolations().toSet())
        }
        val map = mutableMapOf<String, List<MottattPeriode>>()
        map.put("arbeidsforhold1", listOf(MottattPeriode(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 1, 2))))
        val fulltNavn = findLøsningByBehov(Behov.FULLT_NAVN)
        val virksomhet = findLøsningByBehov(Behov.VIRKSOMHET)
        val inntektResultat = findLøsningByBehov(Behov.INNTEKT)
        val inntekt = inntektResultat.value as Inntekt
        return PreutfyltResponse(
            navn = fulltNavn.value as String,
            identitetsnummer = request.identitetsnummer,
            virksomhetsnavn = virksomhet.value as String,
            orgnrUnderenhet = request.orgnrUnderenhet,
            fravaersperiode = map,
            egenmeldingsperioder = listOf(MottattPeriode(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 1, 2))),
            bruttoinntekt = inntekt.bruttoInntekt,
            tidligereinntekt = inntekt.historisk,
            behandlingsperiode = MottattPeriode(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 1, 2)),
            arbeidsforhold = listOf(MottattArbeidsforhold("arbeidsforhold1", "test", 100.0f))
        )
    }

    fun getStatus(): HttpStatusCode {
        if (hasErrors()) {
            return HttpStatusCode.InternalServerError
        }
        return HttpStatusCode.Created
    }
}
