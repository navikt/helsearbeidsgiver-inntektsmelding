package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Bonus
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Ferie
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.FullLonnIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Inntekt
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Naturalytelse
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NaturalytelseKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NyStilling
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NyStillingsprosent
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Permisjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Permittering
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Refusjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Tariffendring
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.VarigLonnsendring
import no.nav.helsearbeidsgiver.felles.test.mock.DELVIS_INNSENDING_REQUEST
import no.nav.helsearbeidsgiver.felles.test.mock.GYLDIG_INNSENDING_REQUEST
import no.nav.helsearbeidsgiver.inntektsmelding.api.TestData
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.validationResponseMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.valiktor.ConstraintViolationException
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals

class InnsendingValidateKtTest {

    private val NOW = LocalDate.now()
    private val MAX_INNTEKT: BigDecimal = 1_000_001.0.toBigDecimal()
    private val MAX_REFUSJON: BigDecimal = 1_000_001.0.toBigDecimal()
    private val NEGATIVT_BELØP: BigDecimal = (-0.1).toBigDecimal()
    private val MAX_NATURAL_BELØP: BigDecimal = 1_000_000.0.toBigDecimal()
    private val ZERO: BigDecimal = 0.toBigDecimal()

    @Test
    fun `skal akseptere gyldig`() {
        GYLDIG_INNSENDING_REQUEST.validate()
    }

    @Test
    fun `skal ikke godta tom liste med arbeidsgiverperioder når arbeidsgiver betaler lønn`() {
        assertThrows<ConstraintViolationException> {
            GYLDIG_INNSENDING_REQUEST.copy(
                fullLønnIArbeidsgiverPerioden = FullLonnIArbeidsgiverPerioden(true),
                arbeidsgiverperioder = emptyList()
            ).validate()
        }
    }

    @Test
    fun `skal godta tom liste med arbeidsgiverperioder når arbeidsgiver ikke betaler lønn`() {
        GYLDIG_INNSENDING_REQUEST.copy(
            fullLønnIArbeidsgiverPerioden = FullLonnIArbeidsgiverPerioden(false, BegrunnelseIngenEllerRedusertUtbetalingKode.FISKER_MED_HYRE),
            arbeidsgiverperioder = emptyList()
        ).validate()
    }

    @Test
    fun `skal ikke godta arbeidsgiverperioder med ugyldig periode (fom ETTER tom))`() {
        assertThrows<ConstraintViolationException> {
            GYLDIG_INNSENDING_REQUEST.copy(
                arbeidsgiverperioder = listOf(Periode(NOW, NOW.minusDays(5)))
            ).validate()
        }
    }

    @Test
    fun `skal godta arbeidsgiverperioder med gyldig periode (fom FØR tom)`() {
        GYLDIG_INNSENDING_REQUEST.copy(
            arbeidsgiverperioder = listOf(Periode(NOW, NOW.plusDays(3)))
        ).validate()
    }

    @Test
    fun `skal godta delvis innsending`() {
        DELVIS_INNSENDING_REQUEST.validate()
    }

    @Test
    fun `skal gi feilmelding når orgnummer er ugyldig`() {
        assertThrows<ConstraintViolationException> {
            GYLDIG_INNSENDING_REQUEST.copy(orgnrUnderenhet = "").validate()
        }
        assertThrows<ConstraintViolationException> {
            GYLDIG_INNSENDING_REQUEST.copy(orgnrUnderenhet = TestData.notValidOrgNr).validate()
        }
    }

    @Test
    fun `skal gi feilmelding når fnr er ugyldig`() {
        assertThrows<ConstraintViolationException> {
            GYLDIG_INNSENDING_REQUEST.copy(identitetsnummer = "").validate()
        }
        assertThrows<ConstraintViolationException> {
            GYLDIG_INNSENDING_REQUEST.copy(identitetsnummer = TestData.notValidIdentitetsnummer).validate()
        }
    }

    @Test
    fun `skal godta tom liste med behandlingsdager`() {
        GYLDIG_INNSENDING_REQUEST.copy(behandlingsdager = emptyList()).validate()
    }

    @Test
    fun `skal godta tom liste med egenmeldinger`() {
        GYLDIG_INNSENDING_REQUEST.copy(egenmeldingsperioder = emptyList()).validate()
    }

    @Test
    fun `skal ikke godta egenmeldinger hvor tom er før fom`() {
        assertThrows<ConstraintViolationException> {
            GYLDIG_INNSENDING_REQUEST.copy(
                egenmeldingsperioder = listOf(
                    Periode(
                        NOW.plusDays(1),
                        NOW
                    )
                )
            ).validate()
        }
    }

    @Test
    fun `skal gi feil dersom bruttoInntekt er for høy`() {
        assertThrows<ConstraintViolationException> {
            val inntekt = GYLDIG_INNSENDING_REQUEST.inntekt.copy(beregnetInntekt = MAX_INNTEKT)
            GYLDIG_INNSENDING_REQUEST.copy(inntekt = inntekt).validate()
        }
    }

    @Test
    fun `skal gi feil dersom bruttoInntekt er negativ`() {
        assertThrows<ConstraintViolationException> {
            val inntekt = GYLDIG_INNSENDING_REQUEST.inntekt.copy(beregnetInntekt = NEGATIVT_BELØP)
            GYLDIG_INNSENDING_REQUEST.copy(inntekt = inntekt).validate()
        }
    }

    @Test
    fun `skal tillate inntekt på 0 kroner`() {
        val inntekt = GYLDIG_INNSENDING_REQUEST.inntekt.copy(beregnetInntekt = ZERO)
        GYLDIG_INNSENDING_REQUEST.copy(inntekt = inntekt).validate()
    }

    @Test
    fun `skal gi feil dersom bruttoInntekt ikke er bekreftet`() {
        assertThrows<ConstraintViolationException> {
            val inntekt = GYLDIG_INNSENDING_REQUEST.inntekt.copy(bekreftet = false)
            GYLDIG_INNSENDING_REQUEST.copy(inntekt = inntekt).validate()
        }
    }

    @Test
    fun `skal gi feil dersom arbeidsgiver ikke betaler lønn og refusjonsbeløp er tom`() {
        assertThrows<ConstraintViolationException> {
            GYLDIG_INNSENDING_REQUEST.copy(
                fullLønnIArbeidsgiverPerioden = FullLonnIArbeidsgiverPerioden(
                    false
                )
            ).validate()
        }
    }

    @Test
    fun `skal gi feil dersom refusjonsbeløp er for høyt`() {
        assertThrows<ConstraintViolationException> {
            GYLDIG_INNSENDING_REQUEST.copy(refusjon = Refusjon(true, MAX_REFUSJON)).validate()
        }
    }

    @Test
    fun `skal gi feil dersom refusjonsbeløp er negativt`() {
        assertThrows<ConstraintViolationException> {
            GYLDIG_INNSENDING_REQUEST.copy(refusjon = Refusjon(true, NEGATIVT_BELØP, NOW)).validate()
        }
    }

    @Test
    fun `skal gi feil dersom refusjonskravet opphører i perioden og dato er tom`() {
        assertThrows<ConstraintViolationException> {
            GYLDIG_INNSENDING_REQUEST.copy(refusjon = Refusjon(true, NEGATIVT_BELØP)).validate()
        }
    }

    @Test
    fun `skal godta tom liste med naturalytelser`() {
        GYLDIG_INNSENDING_REQUEST.copy(naturalytelser = emptyList()).validate()
    }

    @Test
    fun `skal ikke godta naturalytelser med negativt beløp`() {
        assertThrows<ConstraintViolationException> {
            GYLDIG_INNSENDING_REQUEST.copy(
                naturalytelser = listOf(
                    Naturalytelse(
                        NaturalytelseKode.YRKEBILTJENESTLIGBEHOVKILOMETER,
                        NOW,
                        NEGATIVT_BELØP
                    )
                )
            ).validate()
        }
    }

    @Test
    fun `skal ikke godta naturalytelser med for høyt beløp`() {
        assertThrows<ConstraintViolationException> {
            GYLDIG_INNSENDING_REQUEST.copy(
                naturalytelser = listOf(
                    Naturalytelse(
                        NaturalytelseKode.BOLIG,
                        NOW,
                        MAX_NATURAL_BELØP
                    )
                )
            ).validate()
        }
    }

    @Test
    fun `skal gi feil dersom opplysninger ikke er bekreftet`() {
        assertThrows<ConstraintViolationException> {
            GYLDIG_INNSENDING_REQUEST.copy(bekreftOpplysninger = false).validate()
        }
    }

    @Test
    fun `skal bruke språkfil for feil`() {
        try {
            GYLDIG_INNSENDING_REQUEST.copy(
                naturalytelser = listOf(
                    Naturalytelse(
                        NaturalytelseKode.INNBETALINGTILUTENLANDSKPENSJONSORDNING,
                        NOW,
                        MAX_NATURAL_BELØP.plus(1.toBigDecimal())
                    )
                )
            ).validate()
        } catch (ex: ConstraintViolationException) {
            val response = validationResponseMapper(ex.constraintViolations)
            assertEquals("naturalytelser[0].beløp", response.errors[0].property)
            assertEquals("Må være mindre enn 1 000 000,0", response.errors[0].error)
        }
    }

    @Test
    fun `skal godta ulike årsak innsendinger`() {
        GYLDIG_INNSENDING_REQUEST.copy(årsakInnsending = no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.ÅrsakInnsending.NY).validate()
        GYLDIG_INNSENDING_REQUEST.copy(årsakInnsending = no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.ÅrsakInnsending.ENDRING).validate()
    }

    @Test
    fun `skal godta uten endringsårsak`() {
        GYLDIG_INNSENDING_REQUEST.copy(
            inntekt = Inntekt(
                endringÅrsak = null,
                beregnetInntekt = 1.0.toBigDecimal(),
                bekreftet = true,
                manueltKorrigert = false
            )
        ).validate()
    }

    @Test
    fun `skal godta endringsårsak - Tariffendring`() {
        GYLDIG_INNSENDING_REQUEST.copy(
            inntekt = Inntekt(
                endringÅrsak = Tariffendring(LocalDate.now(), LocalDate.now()),
                beregnetInntekt = 1.0.toBigDecimal(),
                bekreftet = true,
                manueltKorrigert = false
            )
        ).validate()
    }

    @Test
    fun `skal godta endringsårsak - Ferie`() {
        GYLDIG_INNSENDING_REQUEST.copy(
            inntekt = Inntekt(
                endringÅrsak = Ferie(
                    listOf(
                        Periode(
                            LocalDate.now(),
                            LocalDate.now()
                        )
                    )
                ),
                beregnetInntekt = 1.0.toBigDecimal(),
                bekreftet = true,
                manueltKorrigert = false
            )
        ).validate()
    }

    @Test
    fun `skal godta endringsårsak - VarigLønnsendring`() {
        GYLDIG_INNSENDING_REQUEST.copy(
            inntekt = Inntekt(
                endringÅrsak = VarigLonnsendring(LocalDate.now()),
                beregnetInntekt = 1.0.toBigDecimal(),
                bekreftet = true,
                manueltKorrigert = false
            )
        ).validate()
    }

    @Test
    fun `skal godta endringsårsak - Permisjon`() {
        GYLDIG_INNSENDING_REQUEST.copy(
            inntekt = Inntekt(
                endringÅrsak = Permisjon(
                    listOf(
                        Periode(
                            LocalDate.now(),
                            LocalDate.now()
                        )
                    )
                ),
                beregnetInntekt = 1.0.toBigDecimal(),
                bekreftet = true,
                manueltKorrigert = false
            )
        ).validate()
    }

    @Test
    fun `skal godta endringsårsak - Permittering`() {
        GYLDIG_INNSENDING_REQUEST.copy(
            inntekt = Inntekt(
                endringÅrsak = Permittering(
                    listOf(
                        Periode(
                            LocalDate.now(),
                            LocalDate.now()
                        )
                    )
                ),
                beregnetInntekt = 1.0.toBigDecimal(),
                bekreftet = true,
                manueltKorrigert = false
            )
        ).validate()
    }

    @Test
    fun `skal godta endringsårsak - NyStilling`() {
        GYLDIG_INNSENDING_REQUEST.copy(
            inntekt = Inntekt(
                endringÅrsak = NyStilling(LocalDate.now()),
                beregnetInntekt = 1.0.toBigDecimal(),
                bekreftet = true,
                manueltKorrigert = false
            )
        ).validate()
    }

    @Test
    fun `skal godta endringsårsak - NyStillingsprosent`() {
        GYLDIG_INNSENDING_REQUEST.copy(
            inntekt = Inntekt(
                endringÅrsak = NyStillingsprosent(LocalDate.now()),
                beregnetInntekt = 1.0.toBigDecimal(),
                bekreftet = true,
                manueltKorrigert = false
            )
        ).validate()
    }

    @Test
    fun `skal godta endringsårsak - Bonus`() {
        GYLDIG_INNSENDING_REQUEST.copy(
            inntekt = Inntekt(
                endringÅrsak = Bonus(),
                beregnetInntekt = 1.0.toBigDecimal(),
                bekreftet = true,
                manueltKorrigert = false
            )
        ).validate()
    }
}
