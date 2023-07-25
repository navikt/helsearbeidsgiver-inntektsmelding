@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.trenger

import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.ForespurtData
import no.nav.helsearbeidsgiver.felles.Inntekt
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
            return DefaultConstraintViolation(DataFelt.ORGNRUNDERENHET.str, løsning.error?.melding ?: "Ukjent feil", FeilmeldingConstraint)
        }
        if (løsning is NavnLøsning) {
            return DefaultConstraintViolation("identitetsnummer", løsning.error?.melding ?: "Ukjent feil", FeilmeldingConstraint)
        }
        return DefaultConstraintViolation("ukjent", løsning.error?.melding ?: "Ukjent feil", FeilmeldingConstraint)
    }

    private fun mapEgenmeldingsperioder(): List<Periode> {
        return resultat.HENT_TRENGER_IM?.value?.egenmeldingsperioder ?: emptyList()
    }

    private fun mapFraværsperiode(): List<Periode> {
        return resultat.HENT_TRENGER_IM?.value?.sykmeldingsperioder ?: emptyList()
    }

    private fun mapForespurtData(): ForespurtData? =
        resultat.HENT_TRENGER_IM?.value?.forespurtData

    private fun mapFulltNavn(): String {
        return resultat.FULLT_NAVN?.value?.navn ?: "Mangler navn"
    }

    private fun mapArbeidsgiver(): String {
        return resultat.VIRKSOMHET?.value ?: "Mangler arbeidsgivers navn"
    }

    private fun mapInntekt(): Inntekt {
        return resultat.INNTEKT?.value ?: Inntekt(emptyList())
    }

    private fun mapIdentitetsNummer(): String {
        return resultat.HENT_TRENGER_IM?.value?.fnr ?: ""
    }

    private fun mapOrgNummer(): String {
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
