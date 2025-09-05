package no.nav.helsearbeidsgiver.inntektsmelding.db

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import no.nav.hag.simba.utils.felles.test.mock.mockSkjemaInntektsmelding
import no.nav.hag.simba.utils.felles.test.mock.randomDigitString
import no.nav.hag.simba.utils.kontrakt.domene.forespoersel.test.mockForespoersel
import no.nav.hag.simba.utils.kontrakt.domene.forespoersel.test.utenPaakrevdAGP
import no.nav.hag.simba.utils.kontrakt.domene.forespoersel.test.utenPaakrevdInntekt
import no.nav.hag.simba.utils.kontrakt.domene.forespoersel.test.utenPaakrevdRefusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Arbeidsgiverperiode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Bonus
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Refusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.RefusjonEndring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.utils.test.date.desember
import no.nav.helsearbeidsgiver.utils.test.date.november

class InntektsmeldingUtilsKtTest :
    FunSpec({
        context("erDuplikatAv") {
            context("_ikke_ duplikater") {
                test("ulike skjema er _ikke_ duplikater") {
                    val gammel = mockSkjemaInntektsmelding()
                    val ny = mockSkjemaInntektsmelding()
                    val forespoersel = mockForespoersel()

                    ny.erDuplikatAv(gammel, forespoersel).shouldBeFalse()
                }

                test("skjema med ulik agp er _ikke_ duplikater") {
                    val gammel = mockSkjemaInntektsmelding()
                    val ny = gammel.copy(agp = nyAgp)
                    val forespoersel = mockForespoersel()

                    ny.erDuplikatAv(gammel, forespoersel).shouldBeFalse()
                }

                test("skjema med ulik inntekt er _ikke_ duplikater") {
                    val gammel = mockSkjemaInntektsmelding()
                    val ny = gammel.copy(inntekt = nyInntekt)
                    val forespoersel = mockForespoersel()

                    ny.erDuplikatAv(gammel, forespoersel).shouldBeFalse()
                }

                test("skjema med ulik refusjon er _ikke_ duplikater") {
                    val gammel = mockSkjemaInntektsmelding()
                    val ny = gammel.copy(refusjon = nyRefusjon)
                    val forespoersel = mockForespoersel()

                    ny.erDuplikatAv(gammel, forespoersel).shouldBeFalse()
                }
            }

            context("duplikater") {
                test("helt like skjema er duplikater") {
                    val gammel = mockSkjemaInntektsmelding()
                    val ny = gammel.copy()
                    val forespoersel = mockForespoersel()

                    ny.erDuplikatAv(gammel, forespoersel).shouldBeTrue()
                }

                test("skjema med ulike avsender-tlf er duplikater") {
                    val gammel = mockSkjemaInntektsmelding()
                    val ny = gammel.copy(avsenderTlf = randomDigitString(8))
                    val forespoersel = mockForespoersel()

                    ny.erDuplikatAv(gammel, forespoersel).shouldBeTrue()
                }

                test("skjema med ulik agp når lik agp ikke er påkrevd er duplikater") {
                    val gammel = mockSkjemaInntektsmelding()
                    val ny = gammel.copy(agp = nyAgp)
                    val forespoersel = mockForespoersel().utenPaakrevdAGP()

                    ny.erDuplikatAv(gammel, forespoersel).shouldBeTrue()
                }

                test("skjema med ulik inntekt når inntekt ikke er påkrevd er duplikater") {
                    val gammel = mockSkjemaInntektsmelding()
                    val ny = gammel.copy(inntekt = nyInntekt)
                    val forespoersel = mockForespoersel().utenPaakrevdInntekt()

                    ny.erDuplikatAv(gammel, forespoersel).shouldBeTrue()
                }

                test("skjema med ulik refusjon når refusjon ikke er påkrevd er duplikater") {
                    val gammel = mockSkjemaInntektsmelding()
                    val ny = gammel.copy(refusjon = nyRefusjon)
                    val forespoersel = mockForespoersel().utenPaakrevdRefusjon()

                    ny.erDuplikatAv(gammel, forespoersel).shouldBeTrue()
                }
            }
        }
    })

private val nyAgp =
    Arbeidsgiverperiode(
        perioder =
            listOf(
                14.desember til 29.desember,
            ),
        egenmeldinger =
            listOf(
                14.desember til 16.desember,
            ),
        redusertLoennIAgp = null,
    )

private val nyInntekt =
    Inntekt(
        beloep = 7707.7,
        inntektsdato = 30.november,
        naturalytelser = emptyList(),
        endringAarsaker = listOf(Bonus),
    )

private val nyRefusjon =
    Refusjon(
        beloepPerMaaned = 4021.1,
        endringer = listOf(RefusjonEndring(beloep = 0.0, startdato = 31.desember)),
    )
