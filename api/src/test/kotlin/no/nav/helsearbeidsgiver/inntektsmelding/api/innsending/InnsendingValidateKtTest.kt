package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Bonus
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Ferie
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.FullLonnIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Inntekt
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Naturalytelse
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NaturalytelseKode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NyStilling
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.NyStillingsprosent
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Nyansatt
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Permisjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Permittering
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Refusjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.RefusjonEndring
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Tariffendring
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.VarigLonnsendring
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.ÅrsakInnsending
import no.nav.helsearbeidsgiver.felles.test.mock.DELVIS_INNSENDING_REQUEST
import no.nav.helsearbeidsgiver.felles.test.mock.GYLDIG_INNSENDING_REQUEST
import no.nav.helsearbeidsgiver.inntektsmelding.api.TestData
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.validationResponseMapper
import org.valiktor.ConstraintViolationException
import java.time.LocalDate

class InnsendingValidateKtTest : FunSpec({

    val now: LocalDate = LocalDate.now()
    val zero = 0.toBigDecimal()
    val maksInntekt = 1_000_001.0.toBigDecimal()
    val maksRefusjon = 1_000_001.0.toBigDecimal()
    val maksNaturalBeloep = 1_000_000.0.toBigDecimal()
    val negativtBeloep = (-0.1).toBigDecimal()

    test("skal akseptere gyldig") {
        GYLDIG_INNSENDING_REQUEST.validate()
    }

    test("skal tillate at refusjon i arbeidsgiverperioden ikke settes (ved delvis innsending)") {
        DELVIS_INNSENDING_REQUEST.validate()
    }

    test("skal gi feil om refusjonIarbeidsgiverperioden ikke settes (ved komplett innsending)") {
        shouldThrowExactly<ConstraintViolationException> {
            GYLDIG_INNSENDING_REQUEST.copy(
                fullLønnIArbeidsgiverPerioden = null
            ).validate()
        }
    }

    test("skal ikke godta tom liste med arbeidsgiverperioder når arbeidsgiver betaler lønn") {
        shouldThrowExactly<ConstraintViolationException> {
            GYLDIG_INNSENDING_REQUEST.copy(
                fullLønnIArbeidsgiverPerioden = FullLonnIArbeidsgiverPerioden(true),
                arbeidsgiverperioder = emptyList()
            ).validate()
        }
    }

    test("skal godta tom liste med arbeidsgiverperioder når arbeidsgiver ikke betaler lønn") {
        GYLDIG_INNSENDING_REQUEST.copy(
            fullLønnIArbeidsgiverPerioden = FullLonnIArbeidsgiverPerioden(
                utbetalerFullLønn = false,
                begrunnelse = BegrunnelseIngenEllerRedusertUtbetalingKode.FISKER_MED_HYRE,
                utbetalt = zero
            ),
            arbeidsgiverperioder = emptyList()
        ).validate()
    }

    test("skal ikke godta arbeidsgiverperioder med ugyldig periode (fom ETTER tom))") {
        shouldThrowExactly<ConstraintViolationException> {
            GYLDIG_INNSENDING_REQUEST.copy(
                arbeidsgiverperioder = listOf(Periode(now, now.minusDays(5)))
            ).validate()
        }
    }

    test("skal godta arbeidsgiverperioder med gyldig periode (fom FØR tom)") {
        GYLDIG_INNSENDING_REQUEST.copy(
            arbeidsgiverperioder = listOf(Periode(now, now.plusDays(3)))
        ).validate()
    }

    test("midlertidig - komplett innsending har forespurtData-liste med minst tre elementer") {
        GYLDIG_INNSENDING_REQUEST.copy(
            forespurtData = listOf("eple", "banan", "appelsin")
        ).validate()
    }

    test("midlertidig - komplett innsending kan også ha ingen eller tom forespurtData-liste") {
        GYLDIG_INNSENDING_REQUEST.copy(
            forespurtData = null
        ).validate()
        GYLDIG_INNSENDING_REQUEST.copy(
            forespurtData = emptyList()
        ).validate()
    }

    test("skal godta delvis innsending") {
        DELVIS_INNSENDING_REQUEST.validate()
    }

    test("skal gi feilmelding når orgnummer er ugyldig") {
        shouldThrowExactly<ConstraintViolationException> {
            GYLDIG_INNSENDING_REQUEST.copy(orgnrUnderenhet = "").validate()
        }
        shouldThrowExactly<ConstraintViolationException> {
            GYLDIG_INNSENDING_REQUEST.copy(orgnrUnderenhet = TestData.notValidOrgNr).validate()
        }
    }

    test("skal gi feilmelding når fnr er ugyldig") {
        shouldThrowExactly<ConstraintViolationException> {
            GYLDIG_INNSENDING_REQUEST.copy(identitetsnummer = "").validate()
        }
        shouldThrowExactly<ConstraintViolationException> {
            GYLDIG_INNSENDING_REQUEST.copy(identitetsnummer = TestData.notValidIdentitetsnummer).validate()
        }
    }

    test("skal gi feilmelding når telefonnummer er ugyldig") {
        shouldThrowExactly<ConstraintViolationException> {
            GYLDIG_INNSENDING_REQUEST.copy(telefonnummer = "313").validate()
        }
    }

    test("skal godta tom liste med behandlingsdager") {
        GYLDIG_INNSENDING_REQUEST.copy(behandlingsdager = emptyList()).validate()
    }

    context(InnsendingRequest::egenmeldingsperioder.name) {
        test("skal godta tom liste med egenmeldinger") {
            GYLDIG_INNSENDING_REQUEST.copy(egenmeldingsperioder = emptyList()).validate()
        }

        test("skal ikke godta egenmeldinger hvor tom er før fom") {
            shouldThrowExactly<ConstraintViolationException> {
                GYLDIG_INNSENDING_REQUEST.copy(
                    egenmeldingsperioder = listOf(
                        Periode(
                            now.plusDays(1),
                            now
                        )
                    )
                ).validate()
            }
        }
    }

    context(InnsendingRequest::inntekt.name) {
        test("skal tillate inntekt på 0 kroner") {
            val inntekt = GYLDIG_INNSENDING_REQUEST.inntekt.copy(beregnetInntekt = zero)
            GYLDIG_INNSENDING_REQUEST.copy(inntekt = inntekt).validate()
        }

        test("skal gi feil dersom beregnetInntekt er for høy") {
            shouldThrowExactly<ConstraintViolationException> {
                val inntekt = GYLDIG_INNSENDING_REQUEST.inntekt.copy(beregnetInntekt = maksInntekt)
                GYLDIG_INNSENDING_REQUEST.copy(inntekt = inntekt).validate()
            }
        }

        test("skal gi feil dersom beregnetInntekt er negativ") {
            try {
                val inntekt = GYLDIG_INNSENDING_REQUEST.inntekt.copy(beregnetInntekt = negativtBeloep)
                GYLDIG_INNSENDING_REQUEST.copy(inntekt = inntekt).validate()
            } catch (ex: ConstraintViolationException) {
                val response = validationResponseMapper(ex.constraintViolations)
                response.errors[0].property shouldBe "inntekt.beregnetInntekt"
                response.errors[0].error shouldBe "Må være større eller lik 0"
            }
        }

        test("skal gi feil dersom beregnetInntekt ikke er bekreftet") {
            shouldThrowExactly<ConstraintViolationException> {
                val inntekt = GYLDIG_INNSENDING_REQUEST.inntekt.copy(bekreftet = false)
                GYLDIG_INNSENDING_REQUEST.copy(inntekt = inntekt).validate()
            }
        }

        context(Inntekt::endringÅrsak.name) {
            withData(
                mapOf(
                    "Uten endringsårsak" to null,
                    "Tariffendring" to Tariffendring(now, now),
                    "Ferie" to Ferie(
                        listOf(
                            Periode(now, now)
                        )
                    ),
                    "VarigLønnsendring" to VarigLonnsendring(now),
                    "Permisjon" to Permisjon(
                        listOf(
                            Periode(now, now)
                        )
                    ),
                    "Permittering" to Permittering(
                        listOf(
                            Periode(now, now)
                        )
                    ),
                    "NyStilling" to NyStilling(now),
                    "NyStillingsprosent" to NyStillingsprosent(now),
                    "Bonus" to Bonus(),
                    "Nyansatt" to Nyansatt()
                )
            ) { endringAarsak ->
                val gyldigInnsending = GYLDIG_INNSENDING_REQUEST.copy(
                    inntekt = Inntekt(
                        endringÅrsak = endringAarsak,
                        beregnetInntekt = 1.0.toBigDecimal(),
                        bekreftet = true,
                        manueltKorrigert = false
                    )
                )

                shouldNotThrowAny {
                    gyldigInnsending.validate()
                }
            }
        }
    }

    context(InnsendingRequest::fullLønnIArbeidsgiverPerioden.name) {
        test("skal gi feil dersom arbeidsgiver ikke betaler lønn og begrunnelse er tom") {
            val ugyldigInnsending = GYLDIG_INNSENDING_REQUEST.copy(
                fullLønnIArbeidsgiverPerioden = FullLonnIArbeidsgiverPerioden(
                    utbetalerFullLønn = false,
                    begrunnelse = null,
                    utbetalt = 1.0.toBigDecimal()
                )
            )

            shouldThrowExactly<ConstraintViolationException> {
                ugyldigInnsending.validate()
            }
        }

        context(FullLonnIArbeidsgiverPerioden::utbetalt.name) {
            withData(
                mapOf(
                    "feiler uten full lønn og utbetalt beløp er tomt" to null,
                    "feiler uten full lønn og utbetalt beløp er negativt" to negativtBeloep,
                    "feiler uten full lønn og utbetalt beløp er over maks" to maksInntekt
                )
            ) { utbetalt ->
                val ugyldigInnsending = GYLDIG_INNSENDING_REQUEST.copy(
                    fullLønnIArbeidsgiverPerioden = FullLonnIArbeidsgiverPerioden(
                        utbetalerFullLønn = false,
                        begrunnelse = BegrunnelseIngenEllerRedusertUtbetalingKode.ARBEID_OPPHOERT,
                        utbetalt = utbetalt
                    )
                )

                shouldThrowExactly<ConstraintViolationException> {
                    ugyldigInnsending.validate()
                }
            }
        }
    }

    context(InnsendingRequest::refusjon.name) {
        withData(
            mapOf(
                "skal gi feil dersom refusjonsbeløp er udefinert" to null,
                "skal gi feil dersom refusjonsbeløp er negativt" to negativtBeloep,
                "skal gi feil dersom refusjonsbeløp er for høyt" to maksRefusjon
            )
        ) { refusjonPrMnd ->
            val ugyldigInnsending = GYLDIG_INNSENDING_REQUEST.copy(
                refusjon = Refusjon(
                    utbetalerHeleEllerDeler = true,
                    refusjonPrMnd = refusjonPrMnd
                )
            )

            shouldThrowExactly<ConstraintViolationException> {
                ugyldigInnsending.validate()
            }
        }

        context(Refusjon::refusjonEndringer.name) {
            test("Refusjon skal være større enn 0") {
                val gyldigInnsending = GYLDIG_INNSENDING_REQUEST.copy(
                    refusjon = Refusjon(
                        utbetalerHeleEllerDeler = true,
                        refusjonPrMnd = 1.0.toBigDecimal(),
                        refusjonEndringer = null
                    )
                )
                shouldNotThrowAny {
                    gyldigInnsending.validate()
                }
            }

            test("Refusjonendring beløp kan være 0") {
                val gyldigInnsending = GYLDIG_INNSENDING_REQUEST.copy(
                    refusjon = Refusjon(
                        utbetalerHeleEllerDeler = true,
                        refusjonPrMnd = 1.0.toBigDecimal(),
                        refusjonEndringer = listOf(RefusjonEndring(zero, now))
                    )
                )
                shouldNotThrowAny {
                    gyldigInnsending.validate()
                }
            }

            test("endringer på refusjon er ikke påkrevd") {}
            withData(
                mapOf(
                    "feiler ved endring av refusjon uten definert beløp" to RefusjonEndring(null, now),
                    "feiler ved endring av refusjon til negativt beløp" to RefusjonEndring(negativtBeloep, now),
                    "feiler ved endring av refusjon til over maksimalt beløp" to RefusjonEndring(maksRefusjon, now),
                    "feiler ved endring av refusjon uten satt dato" to RefusjonEndring(1.0.toBigDecimal(), null)
                )
            ) { refusjonEndring ->
                val ugyldigInnsending = GYLDIG_INNSENDING_REQUEST.copy(
                    refusjon = Refusjon(
                        utbetalerHeleEllerDeler = true,
                        refusjonPrMnd = 1.0.toBigDecimal(),
                        refusjonEndringer = listOf(refusjonEndring)
                    )
                )

                shouldThrowExactly<ConstraintViolationException> {
                    ugyldigInnsending.validate()
                }
            }
        }
    }

    context(InnsendingRequest::naturalytelser.name) {
        test("skal godta tom liste med naturalytelser") {
            GYLDIG_INNSENDING_REQUEST.copy(naturalytelser = emptyList()).validate()
        }

        withData(
            mapOf(
                "skal ikke godta naturalytelser med negativt beløp" to negativtBeloep,
                "skal ikke godta naturalytelser med for høyt beløp" to maksNaturalBeloep
            )
        ) { beloep ->
            val ugyldigInnsending = GYLDIG_INNSENDING_REQUEST.copy(
                naturalytelser = listOf(
                    Naturalytelse(
                        naturalytelse = NaturalytelseKode.KOSTDOEGN,
                        dato = now,
                        beløp = beloep
                    )
                )
            )

            shouldThrowExactly<ConstraintViolationException> {
                ugyldigInnsending.validate()
            }
        }
    }
    test("skal gi feil dersom opplysninger ikke er bekreftet") {
        shouldThrowExactly<ConstraintViolationException> {
            GYLDIG_INNSENDING_REQUEST.copy(bekreftOpplysninger = false).validate()
        }
    }

    test("skal godta ulike årsak innsendinger") {
        GYLDIG_INNSENDING_REQUEST.copy(årsakInnsending = ÅrsakInnsending.NY).validate()
        GYLDIG_INNSENDING_REQUEST.copy(årsakInnsending = ÅrsakInnsending.ENDRING).validate()
    }

    test("skal bruke språkfil for feil") {
        try {
            GYLDIG_INNSENDING_REQUEST.copy(
                naturalytelser = listOf(
                    Naturalytelse(
                        NaturalytelseKode.BIL,
                        now,
                        maksNaturalBeloep.plus(1.toBigDecimal())
                    )
                )
            ).validate()
        } catch (ex: ConstraintViolationException) {
            val response = validationResponseMapper(ex.constraintViolations)
            response.errors[0].property shouldBe "naturalytelser[0].beløp"
            response.errors[0].error shouldBe "Må være mindre enn 1 000 000,0"
        }
    }
})
