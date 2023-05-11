@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

import no.nav.helsearbeidsgiver.felles.ForespurtData
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Løsning
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.Periode
import no.nav.helsearbeidsgiver.felles.Resultat
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning
import no.nav.helsearbeidsgiver.inntektsmelding.api.mapper.ResultatMapper
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.FeilmeldingConstraint
import org.valiktor.ConstraintViolation
import org.valiktor.DefaultConstraintViolation

class TrengerMapper(
    resultat: Resultat
) : ResultatMapper<TrengerResponse>(resultat) {

    override fun mapConstraint(løsning: Løsning): ConstraintViolation {
        if (løsning is VirksomhetLøsning) {
            return DefaultConstraintViolation(Key.ORGNRUNDERENHET.str, løsning.error?.melding ?: "Ukjent feil", FeilmeldingConstraint)
        }
        if (løsning is NavnLøsning) {
            return DefaultConstraintViolation("identitetsnummer", løsning.error?.melding ?: "Ukjent feil", FeilmeldingConstraint)
        }
        return DefaultConstraintViolation("ukjent", løsning.error?.melding ?: "Ukjent feil", FeilmeldingConstraint)
    }

    fun mapEgenmeldingsperioder(): List<Periode> {
        return resultat.HENT_TRENGER_IM?.value?.egenmeldingsperioder ?: emptyList()
    }

    fun mapFraværsperiode(): List<Periode> {
        return resultat.HENT_TRENGER_IM?.value?.sykmeldingsperioder ?: emptyList()
    }

    fun mapForespurtData(): List<ForespurtData> =
        resultat.HENT_TRENGER_IM?.value?.forespurtData ?: emptyList()

    fun mapFulltNavn(): String {
        return resultat.FULLT_NAVN?.value?.navn ?: "Mangler navn"
    }

    fun mapArbeidsgiver(): String {
        return resultat.VIRKSOMHET?.value ?: "Mangler arbeidsgivers navn"
    }

    fun mapInntekt(): Inntekt {
        return resultat.INNTEKT?.value ?: Inntekt(emptyList())
    }

    fun mapIdentitetsNummer(): String {
        return resultat.HENT_TRENGER_IM?.value?.fnr ?: ""
    }

    fun mapOrgNummer(): String {
        return resultat.HENT_TRENGER_IM?.value?.orgnr ?: ""
    }

    override fun getResultatResponse(): TrengerResponse {
        val inntekt = mapInntekt()
        return TrengerResponse(
            navn = mapFulltNavn(),
            orgNavn = mapArbeidsgiver(),
            identitetsnummer = mapIdentitetsNummer(),
            orgnrUnderenhet = mapOrgNummer(),
            fravaersperioder = mapFraværsperiode(),
            egenmeldingsperioder = mapEgenmeldingsperioder(),
            bruttoinntekt = inntekt.gjennomsnitt(),
            tidligereinntekter = inntekt.historisk,
            behandlingsperiode = null,
            behandlingsdager = emptyList(),
            forespurtData = mapForespurtData()
        )
    }
}
