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

    fun mapEgenmeldingsperioder(): List<MottattPeriode> {
        return listOf(MottattPeriode(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 1, 2)))
    }

    fun mapBehandlingsperiode(): MottattPeriode {
        return MottattPeriode(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 1, 2))
    }

    fun mapArbeidsforhold(): List<MottattArbeidsforhold> {
        return listOf(MottattArbeidsforhold("arbeidsforhold1", "test", 100.0f))
    }

    fun mapFraværsperiode(): MutableMap<String, List<MottattPeriode>> {
        val map = mutableMapOf<String, List<MottattPeriode>>()
        map.put("arbeidsforhold1", listOf(MottattPeriode(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 1, 1))))
        map.put("arbeidsforhold2", listOf(MottattPeriode(LocalDate.of(2022, 1, 2), LocalDate.of(2022, 1, 2))))
        map.put("arbeidsforhold3", listOf(MottattPeriode(LocalDate.of(2022, 1, 3), LocalDate.of(2022, 1, 3))))
        return map
    }

    fun mapFulltNavn(): String {
        return findLøsningByBehov(Behov.FULLT_NAVN).value as String
    }

    fun mapVirksomhet(): String {
        return findLøsningByBehov(Behov.VIRKSOMHET).value as String
    }

    fun mapInntekt(): Inntekt {
        return findLøsningByBehov(Behov.INNTEKT).value as Inntekt
    }

    fun getResponse(): PreutfyltResponse {
        if (hasErrors()) {
            throw ConstraintViolationException(getConstraintViolations().toSet())
        }
        val inntekt = mapInntekt()
        return PreutfyltResponse(
            navn = mapFulltNavn(),
            identitetsnummer = request.identitetsnummer,
            virksomhetsnavn = mapVirksomhet(),
            orgnrUnderenhet = request.orgnrUnderenhet,
            fravaersperiode = mapFraværsperiode(),
            egenmeldingsperioder = mapEgenmeldingsperioder(),
            bruttoinntekt = inntekt.bruttoInntekt,
            tidligereinntekt = inntekt.historisk,
            behandlingsperiode = mapBehandlingsperiode(),
            arbeidsforhold = mapArbeidsforhold()
        )
    }

    fun getStatus(): HttpStatusCode {
        if (hasErrors()) {
            return HttpStatusCode.InternalServerError
        }
        return HttpStatusCode.Created
    }
}
