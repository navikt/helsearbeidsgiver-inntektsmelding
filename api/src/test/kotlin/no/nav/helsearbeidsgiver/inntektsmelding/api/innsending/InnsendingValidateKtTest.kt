package no.nav.helsearbeidsgiver.inntektsmelding.api.innsending

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Bonus
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Ferie
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.FullLoennIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Naturalytelse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.NaturalytelseKode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.NyStilling
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.NyStillingsprosent
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Nyansatt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Permisjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Permittering
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Refusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.RefusjonEndring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Tariffendring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.VarigLonnsendring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.felles.test.mock.delvisInnsendingRequest
import no.nav.helsearbeidsgiver.felles.test.mock.gyldigInnsendingRequest
import no.nav.helsearbeidsgiver.inntektsmelding.api.validation.validationResponseMapper
import org.valiktor.ConstraintViolationException
import java.time.LocalDate

class InnsendingValidateKtTest :
    FunSpec({

        val now: LocalDate = LocalDate.now()
        val zero = 0.0
        val maksInntekt = 1_000_001.0
        val maksRefusjon = 1_000_001.0
        val maksNaturalBeloep = 1_000_000.0
        val negativtBeloep = -0.1
        val hoeyereEnnInntekt = gyldigInnsendingRequest.inntekt.beregnetInntekt.plus(1)

        test("godtar fullstendig innsending") {
            gyldigInnsendingRequest.validate()
        }

        test("godtar delvis innsending") {
            delvisInnsendingRequest.validate()
        }

        test("skal gi feil om refusjonIarbeidsgiverperioden ikke settes (ved komplett innsending)") {
            shouldThrowExactly<ConstraintViolationException> {
                gyldigInnsendingRequest
                    .copy(
                        fullLønnIArbeidsgiverPerioden = null,
                    ).validate()
            }
        }

        test("skal ikke godta tom liste med arbeidsgiverperioder når arbeidsgiver betaler lønn") {
            shouldThrowExactly<ConstraintViolationException> {
                gyldigInnsendingRequest
                    .copy(
                        fullLønnIArbeidsgiverPerioden = FullLoennIArbeidsgiverPerioden(true),
                        arbeidsgiverperioder = emptyList(),
                    ).validate()
            }
        }

        test("skal godta tom liste med arbeidsgiverperioder når arbeidsgiver ikke betaler lønn") {
            gyldigInnsendingRequest
                .copy(
                    fullLønnIArbeidsgiverPerioden =
                        FullLoennIArbeidsgiverPerioden(
                            utbetalerFullLønn = false,
                            begrunnelse = BegrunnelseIngenEllerRedusertUtbetalingKode.FiskerMedHyre,
                            utbetalt = zero,
                        ),
                    arbeidsgiverperioder = emptyList(),
                ).validate()
        }

        test("skal godta arbeidsgiverperioder med gyldig periode (fom FØR tom)") {
            gyldigInnsendingRequest
                .copy(
                    arbeidsgiverperioder = listOf(Periode(now, now.plusDays(3))),
                ).validate()
        }

        test("midlertidig - komplett innsending har forespurtData-liste med minst tre elementer") {
            gyldigInnsendingRequest
                .copy(
                    forespurtData = listOf("eple", "banan", "appelsin"),
                ).validate()
        }

        test("midlertidig - komplett innsending kan også ha ingen eller tom forespurtData-liste") {
            gyldigInnsendingRequest
                .copy(
                    forespurtData = null,
                ).validate()
            gyldigInnsendingRequest
                .copy(
                    forespurtData = emptyList(),
                ).validate()
        }

        test("skal gi feilmelding når orgnummer er ugyldig") {
            shouldThrowExactly<ConstraintViolationException> {
                gyldigInnsendingRequest.copy(orgnrUnderenhet = "").validate()
            }
            shouldThrowExactly<ConstraintViolationException> {
                gyldigInnsendingRequest.copy(orgnrUnderenhet = TestData.NOT_VALID_ORGNR).validate()
            }
        }

        test("skal gi feilmelding når fnr er ugyldig") {
            shouldThrowExactly<ConstraintViolationException> {
                gyldigInnsendingRequest.copy(identitetsnummer = "").validate()
            }
            shouldThrowExactly<ConstraintViolationException> {
                gyldigInnsendingRequest.copy(identitetsnummer = TestData.NOT_VALID_IDENTITETSNUMMER).validate()
            }
        }

        test("skal gi feilmelding når telefonnummer er ugyldig") {
            shouldThrowExactly<ConstraintViolationException> {
                gyldigInnsendingRequest.copy(telefonnummer = "313").validate()
            }
        }

        test("skal godta tom liste med behandlingsdager") {
            gyldigInnsendingRequest.copy(behandlingsdager = emptyList()).validate()
        }

        test("skal godta tom liste med egenmeldinger") {
            gyldigInnsendingRequest.copy(egenmeldingsperioder = emptyList()).validate()
        }

        context(Innsending::inntekt.name) {
            test("skal tillate inntekt på 0 kroner") {
                val inntekt = gyldigInnsendingRequest.inntekt.copy(beregnetInntekt = zero)
                gyldigInnsendingRequest
                    .copy(
                        inntekt = inntekt,
                        refusjon = Refusjon(utbetalerHeleEllerDeler = false),
                    ).validate()
            }

            test("skal gi feil dersom beregnetInntekt er for høy") {
                shouldThrowExactly<ConstraintViolationException> {
                    val inntekt = gyldigInnsendingRequest.inntekt.copy(beregnetInntekt = maksInntekt)
                    gyldigInnsendingRequest.copy(inntekt = inntekt).validate()
                }
            }

            test("skal gi feil dersom beregnetInntekt er negativ") {
                try {
                    val inntekt = gyldigInnsendingRequest.inntekt.copy(beregnetInntekt = negativtBeloep)
                    gyldigInnsendingRequest.copy(inntekt = inntekt).validate()
                } catch (ex: ConstraintViolationException) {
                    val response = validationResponseMapper(ex.constraintViolations)
                    response.errors[0].property shouldBe "inntekt.beregnetInntekt"
                    response.errors[0].error shouldBe "Må være større eller lik 0"
                }
            }

            test("skal gi feil dersom beregnetInntekt ikke er bekreftet") {
                shouldThrowExactly<ConstraintViolationException> {
                    val inntekt = gyldigInnsendingRequest.inntekt.copy(bekreftet = false)
                    gyldigInnsendingRequest.copy(inntekt = inntekt).validate()
                }
            }

            context(Inntekt::endringÅrsak.name) {
                withData(
                    mapOf(
                        "Uten endringsårsak" to null,
                        "Tariffendring" to Tariffendring(now, now),
                        "Ferie" to
                            Ferie(
                                listOf(
                                    Periode(now, now),
                                ),
                            ),
                        "VarigLønnsendring" to VarigLonnsendring(now),
                        "Permisjon" to
                            Permisjon(
                                listOf(
                                    Periode(now, now),
                                ),
                            ),
                        "Permittering" to
                            Permittering(
                                listOf(
                                    Periode(now, now),
                                ),
                            ),
                        "NyStilling" to NyStilling(now),
                        "NyStillingsprosent" to NyStillingsprosent(now),
                        "Bonus" to Bonus(),
                        "Nyansatt" to Nyansatt,
                    ),
                ) { endringAarsak ->
                    val gyldigInnsending =
                        gyldigInnsendingRequest.copy(
                            inntekt =
                                Inntekt(
                                    endringÅrsak = endringAarsak,
                                    beregnetInntekt = gyldigInnsendingRequest.inntekt.beregnetInntekt,
                                    bekreftet = true,
                                    manueltKorrigert = false,
                                ),
                        )

                    shouldNotThrowAny {
                        gyldigInnsending.validate()
                    }
                }
            }
        }

        context(Innsending::fullLønnIArbeidsgiverPerioden.name) {
            test("skal gi feil dersom arbeidsgiver ikke betaler lønn og begrunnelse er tom") {
                val ugyldigInnsending =
                    gyldigInnsendingRequest.copy(
                        fullLønnIArbeidsgiverPerioden =
                            FullLoennIArbeidsgiverPerioden(
                                utbetalerFullLønn = false,
                                begrunnelse = null,
                                utbetalt = 1.0,
                            ),
                    )

                shouldThrowExactly<ConstraintViolationException> {
                    ugyldigInnsending.validate()
                }
            }

            context(FullLoennIArbeidsgiverPerioden::utbetalt.name) {
                withData(
                    mapOf(
                        "feiler uten full lønn og utbetalt beløp er tomt" to null,
                        "feiler uten full lønn og utbetalt beløp er negativt" to negativtBeloep,
                        "feiler uten full lønn og utbetalt beløp er over maks" to maksInntekt,
                    ),
                ) { utbetalt ->
                    val ugyldigInnsending =
                        gyldigInnsendingRequest.copy(
                            fullLønnIArbeidsgiverPerioden =
                                FullLoennIArbeidsgiverPerioden(
                                    utbetalerFullLønn = false,
                                    begrunnelse = BegrunnelseIngenEllerRedusertUtbetalingKode.ArbeidOpphoert,
                                    utbetalt = utbetalt,
                                ),
                        )

                    shouldThrowExactly<ConstraintViolationException> {
                        ugyldigInnsending.validate()
                    }
                }
            }
        }

        context(Innsending::refusjon.name) {
            withData(
                mapOf(
                    "skal gi feil dersom refusjonsbeløp er udefinert" to null,
                    "skal gi feil dersom refusjonsbeløp er negativt" to negativtBeloep,
                    "skal gi feil dersom refusjonsbeløp er for høyt" to maksRefusjon,
                    "skal gi feil dersom refusjonsbeløp er høyere enn inntekt" to hoeyereEnnInntekt,
                ),
            ) { refusjonPrMnd ->
                val ugyldigInnsending =
                    gyldigInnsendingRequest.copy(
                        refusjon =
                            Refusjon(
                                utbetalerHeleEllerDeler = true,
                                refusjonPrMnd = refusjonPrMnd,
                            ),
                    )

                shouldThrowExactly<ConstraintViolationException> {
                    ugyldigInnsending.validate()
                }
            }

            context(Refusjon::refusjonEndringer.name) {
                test("Refusjon skal være større enn 0") {
                    val gyldigInnsending =
                        gyldigInnsendingRequest.copy(
                            refusjon =
                                Refusjon(
                                    utbetalerHeleEllerDeler = true,
                                    refusjonPrMnd = 1.0,
                                    refusjonEndringer = null,
                                ),
                        )
                    shouldNotThrowAny {
                        gyldigInnsending.validate()
                    }
                }

                test("Refusjonendring beløp kan være 0") {
                    val gyldigInnsending =
                        gyldigInnsendingRequest.copy(
                            refusjon =
                                Refusjon(
                                    utbetalerHeleEllerDeler = true,
                                    refusjonPrMnd = 1.0,
                                    refusjonEndringer = listOf(RefusjonEndring(zero, now)),
                                ),
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
                        "feiler ved endring av refusjon til høyere enn inntekt" to RefusjonEndring(hoeyereEnnInntekt, now),
                        "feiler ved endring av refusjon uten satt dato" to RefusjonEndring(1.0, null),
                    ),
                ) { refusjonEndring ->
                    val ugyldigInnsending =
                        gyldigInnsendingRequest.copy(
                            refusjon =
                                Refusjon(
                                    utbetalerHeleEllerDeler = true,
                                    refusjonPrMnd = 1.0,
                                    refusjonEndringer = listOf(refusjonEndring),
                                ),
                        )

                    shouldThrowExactly<ConstraintViolationException> {
                        ugyldigInnsending.validate()
                    }
                }
            }
        }

        context(Innsending::naturalytelser.name) {
            test("skal godta tom liste med naturalytelser") {
                gyldigInnsendingRequest.copy(naturalytelser = emptyList()).validate()
            }

            withData(
                mapOf(
                    "skal ikke godta naturalytelser med negativt beløp" to negativtBeloep,
                    "skal ikke godta naturalytelser med for høyt beløp" to maksNaturalBeloep,
                ),
            ) { beloep ->
                val ugyldigInnsending =
                    gyldigInnsendingRequest.copy(
                        naturalytelser =
                            listOf(
                                Naturalytelse(
                                    naturalytelse = NaturalytelseKode.KOSTDOEGN,
                                    dato = now,
                                    beløp = beloep,
                                ),
                            ),
                    )

                shouldThrowExactly<ConstraintViolationException> {
                    ugyldigInnsending.validate()
                }
            }
        }
        test("skal gi feil dersom opplysninger ikke er bekreftet") {
            shouldThrowExactly<ConstraintViolationException> {
                gyldigInnsendingRequest.copy(bekreftOpplysninger = false).validate()
            }
        }

        test("skal godta ulike årsak innsendinger") {
            gyldigInnsendingRequest.copy(årsakInnsending = AarsakInnsending.NY).validate()
            gyldigInnsendingRequest.copy(årsakInnsending = AarsakInnsending.ENDRING).validate()
        }

        test("skal bruke språkfil for feil") {
            try {
                gyldigInnsendingRequest
                    .copy(
                        naturalytelser =
                            listOf(
                                Naturalytelse(
                                    NaturalytelseKode.BIL,
                                    now,
                                    maksNaturalBeloep.plus(1),
                                ),
                            ),
                    ).validate()
            } catch (ex: ConstraintViolationException) {
                val response = validationResponseMapper(ex.constraintViolations)
                response.errors[0].property shouldBe "naturalytelser[0].beløp"
                response.errors[0].error shouldBe "Må være mindre enn 1 000 000"
            }
        }
    })

private object TestData {
    const val NOT_VALID_IDENTITETSNUMMER = "50012001987"
    const val NOT_VALID_ORGNR = "123456789"
}
