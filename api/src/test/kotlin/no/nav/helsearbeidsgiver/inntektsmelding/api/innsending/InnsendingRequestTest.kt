@file:Suppress("NonAsciiCharacters")

package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.inntektsmelding.api.TestData
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.validationResponseMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.valiktor.ConstraintViolationException
import java.time.LocalDate
import kotlin.test.assertEquals

internal class InnsendingRequestTest {

    private val NOW = LocalDate.now()
    private val MAX_INNTEKT: Double = 1_000_001.0
    private val MAX_REFUSJON: Double = 1_000_001.0
    private val BELØP_NULL: Double = 0.0
    private val NEGATIVT_BELØP: Double = -0.1
    private val MAX_NATURAL_BELØP: Double = 1_000_000.0

    @Test
    fun `skal akseptere gyldig`() {
        GYLDIG.validate()
    }

    @Test
    fun `skal kunne konvertere til json`() {
        println(customObjectMapper().writeValueAsString(GYLDIG))
    }

    @Test
    fun `skal gi feilmelding når orgnummer er ugyldig`() {
        assertThrows<ConstraintViolationException> {
            GYLDIG.copy(orgnrUnderenhet = "").validate()
        }
        assertThrows<ConstraintViolationException> {
            GYLDIG.copy(orgnrUnderenhet = TestData.notValidOrgNr).validate()
        }
    }

    @Test
    fun `skal gi feilmelding når fnr er ugyldig`() {
        assertThrows<ConstraintViolationException> {
            GYLDIG.copy(identitetsnummer = "").validate()
        }
        assertThrows<ConstraintViolationException> {
            GYLDIG.copy(identitetsnummer = TestData.notValidIdentitetsnummer).validate()
        }
    }

    @Test
    fun `skal gi feilmelding når behandlingsperiode tom er før fom`() {
        assertThrows<ConstraintViolationException> {
            GYLDIG.copy(behandlingsdagerFom = NOW, behandlingsdagerTom = NOW.minusDays(3)).validate()
        }
        GYLDIG.copy(behandlingsdagerFom = NOW, behandlingsdagerTom = NOW.plusDays(3)).validate()
    }

    @Test
    fun `skal godta tom liste med behandlingsdager`() {
        GYLDIG.copy(behandlingsdager = emptyList()).validate()
    }

    @Test
    fun `skal gi feil dersom det er brudd på regler for behandlingsdager`() {
        // TODO
    }

    @Test
    fun `skal godta tom liste med egenmeldinger`() {
        GYLDIG.copy(egenmeldinger = emptyList()).validate()
    }

    @Test
    fun `skal ikke godta egenmeldinger hvor tom er før fom`() {
        assertThrows<ConstraintViolationException> {
            GYLDIG.copy(egenmeldinger = listOf(Egenmelding(NOW.plusDays(1), NOW))).validate()
        }
    }

    @Test
    fun `skal gi feil dersom bruttoInntekt er tom`() {
        assertThrows<ConstraintViolationException> {
            GYLDIG.copy(bruttoInntekt = 0.0).validate()
        }
    }

    @Test
    fun `skal gi feil dersom bruttoInntekt er for høy`() {
        assertThrows<ConstraintViolationException> {
            GYLDIG.copy(bruttoInntekt = MAX_INNTEKT).validate()
        }
    }

    @Test
    fun `skal gi feil dersom bruttoInntekt er negativ`() {
        assertThrows<ConstraintViolationException> {
            GYLDIG.copy(bruttoInntekt = NEGATIVT_BELØP).validate()
        }
    }

    @Test
    fun `skal gi feil dersom bruttoInntekt ikke er bekreftet`() {
        assertThrows<ConstraintViolationException> {
            GYLDIG.copy(bruttoBekreftet = false).validate()
        }
    }

    @Test
    fun `skal gi feil dersom arbeidsgiver ikke betaler lønn og refusjonsbeløp er tom`() {
        assertThrows<ConstraintViolationException> {
            GYLDIG.copy(utbetalerFull = true, refusjonPrMnd = BELØP_NULL).validate()
        }
    }

    @Test
    fun `skal gi feil dersom refusjonsbeløp er for høyt`() {
        assertThrows<ConstraintViolationException> {
            GYLDIG.copy(utbetalerFull = true, refusjonPrMnd = MAX_REFUSJON).validate()
        }
    }

    @Test
    fun `skal gi feil dersom refusjonsbeløp er negativt`() {
        assertThrows<ConstraintViolationException> {
            GYLDIG.copy(utbetalerFull = true, refusjonPrMnd = NEGATIVT_BELØP).validate()
        }
    }

    @Test
    fun `skal gi feil dersom refusjonskravet opphører i perioden og dato er tom`() {
        assertThrows<ConstraintViolationException> {
            GYLDIG.copy(opphørerKravet = true, opphørSisteDag = null).validate()
        }
    }

    @Test
    fun `skal ikke gi feil dersom refusjonskravet opphører i perioden og dato er tom`() {
        GYLDIG.copy(opphørerKravet = false, opphørSisteDag = null).validate()
    }

    @Test
    fun `skal godta tom liste med naturalytelser`() {
        GYLDIG.copy(naturalytelser = emptyList()).validate()
    }

    @Test
    fun `skal ikke godta ytelse som ikke er i lista`() {
        // TODO
    }

    @Test
    fun `skal ikke godta naturalytelser med negativt beløp`() {
        assertThrows<ConstraintViolationException> {
            GYLDIG.copy(naturalytelser = listOf(Naturalytelse(NaturalytelseKode.kostDoegn, NOW, NEGATIVT_BELØP))).validate()
        }
    }

    @Test
    fun `skal ikke godta naturalytelser med for høyt beløp`() {
        assertThrows<ConstraintViolationException> {
            GYLDIG.copy(naturalytelser = listOf(Naturalytelse(NaturalytelseKode.kostDoegn, NOW, MAX_NATURAL_BELØP))).validate()
        }
    }

    @Test
    fun `skal gi feil dersom opplysninger ikke er bekreftet`() {
        assertThrows<ConstraintViolationException> {
            GYLDIG.copy(bekreftOpplysninger = false).validate()
        }
    }

    @Test
    fun `skal bruke språkfil for feil`() {
        try {
            GYLDIG.copy(naturalytelser = listOf(Naturalytelse(NaturalytelseKode.kostDoegn, NOW, MAX_NATURAL_BELØP + 1))).validate()
        } catch (ex: ConstraintViolationException) {
            val response = validationResponseMapper(ex.constraintViolations)
            assertEquals("naturalytelser[0].beløp", response.errors[0].property)
            assertEquals("Må være mindre enn 1 000 000", response.errors[0].error)
        }
    }
}
