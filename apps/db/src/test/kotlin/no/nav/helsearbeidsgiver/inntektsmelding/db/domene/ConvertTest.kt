@file:Suppress("DEPRECATION")

package no.nav.helsearbeidsgiver.inntektsmelding.db.domene

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.hag.simba.utils.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Arbeidsgiverperiode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Bonus
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Feilregistrert
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Ferie
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Ferietrekk
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Naturalytelse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.NyStilling
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.NyStillingsprosent
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Nyansatt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Permisjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Permittering
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.RedusertLoennIAgp
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Refusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.RefusjonEndring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Sykefravaer
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Tariffendring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.VarigLoennsendring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.oktober
import no.nav.helsearbeidsgiver.utils.test.date.september
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.LocalDate
import java.util.UUID

class ConvertTest :
    FunSpec({

        val foersteJanuar2023 = LocalDate.of(2023, 1, 1)
        val inntektsmeldingId = UUID.randomUUID()
        val forespurtType =
            Inntektsmelding.Type.Forespurt(
                id = UUID.randomUUID(),
            )

        test("convertInntekt") {
            val im = lagGammelInntektsmelding()
            val nyInntekt = im.convertInntekt()
            nyInntekt shouldNotBe null
            val gammelInntekt = im.inntekt
            nyInntekt?.beloep shouldBe gammelInntekt?.beregnetInntekt
        }

        test("convertEndringAarsak") {
            BonusGammeltFormat(1.0, LocalDate.EPOCH).convert() shouldBe Bonus
            BonusGammeltFormat(null, null).convert() shouldBe Bonus
            FeilregistrertGammeltFormat.convert() shouldBe Feilregistrert
            FerieGammeltFormat(lagPeriode()).convert() shouldBe Ferie(lagPeriode())
            FerietrekkGammeltFormat.convert() shouldBe Ferietrekk
            NyansattGammeltFormat.convert() shouldBe Nyansatt
            NyStillingGammeltFormat(foersteJanuar2023).convert() shouldBe NyStilling(foersteJanuar2023)
            NyStillingsprosentGammeltFormat(foersteJanuar2023).convert() shouldBe NyStillingsprosent(foersteJanuar2023)
            PermisjonGammeltFormat(lagPeriode()).convert() shouldBe Permisjon(lagPeriode())
            PermitteringGammeltFormat(lagPeriode()).convert() shouldBe Permittering(lagPeriode())
            SykefravaerGammeltFormat(lagPeriode()).convert() shouldBe Sykefravaer(lagPeriode())
            TariffendringGammeltFormat(foersteJanuar2023, foersteJanuar2023).convert() shouldBe
                Tariffendring(
                    foersteJanuar2023,
                    foersteJanuar2023,
                )
            VarigLonnsendringGammeltFormat(foersteJanuar2023).convert() shouldBe VarigLoennsendring(foersteJanuar2023)
        }

        test("Naturalytelse-convert") {
            val belop = 10.0
            val gamleYtelser =
                NaturalytelseKodeGammeltFormat.entries
                    .map {
                        NaturalytelseGammeltFormat(it, foersteJanuar2023, belop)
                    }.toList()
            val nyeYtelser =
                Naturalytelse.Kode.entries
                    .map {
                        Naturalytelse(it, belop, foersteJanuar2023)
                    }.toList()
            gamleYtelser.map { it.convert() } shouldBeEqual nyeYtelser
        }

        test("FullLoennIArbeidsgiverPerioden-convert") {
            val utbetalt = 10000.0
            val imMedReduksjon =
                lagGammelInntektsmelding().copy(
                    fullLønnIArbeidsgiverPerioden =
                        FullLoennIAgpGammeltFormat(
                            false,
                            BegrunnelseIngenEllerRedusertUtbetalingKodeGammeltFormat.BetvilerArbeidsufoerhet,
                            utbetalt,
                        ),
                )
            val agp = imMedReduksjon.convertAgp()
            agp?.redusertLoennIAgp?.beloep shouldBe utbetalt
            agp?.redusertLoennIAgp?.begrunnelse shouldBe RedusertLoennIAgp.Begrunnelse.BetvilerArbeidsufoerhet
        }

        test("convertBegrunnelse") {
            val gamleBegrunnelser = BegrunnelseIngenEllerRedusertUtbetalingKodeGammeltFormat.entries.toList()
            val nyeBegrunnelser = RedusertLoennIAgp.Begrunnelse.entries.toList()
            gamleBegrunnelser.forEachIndexed { index, begrunnelse -> begrunnelse.convert() shouldBe nyeBegrunnelser[index] }
        }

        test("håndterer tomme lister og null-verdier") {
            val im = lagGammelInntektsmeldingMedTommeOgNullVerdier()
            im.convertAgp()
            im.convertInntekt()
            im.refusjon.convert()
        }

        test("konverter fra nytt til gammelt IM-format") {
            val nyIM =
                mockInntektsmeldingV1().copy(
                    id = inntektsmeldingId,
                    type = forespurtType,
                )
            val gammelIM = nyIM.convert()

            gammelIM.vedtaksperiodeId shouldBe nyIM.vedtaksperiodeId
            gammelIM.orgnrUnderenhet shouldBe nyIM.avsender.orgnr.verdi
            gammelIM.identitetsnummer shouldBe nyIM.sykmeldt.fnr.verdi
            gammelIM.fraværsperioder shouldBe nyIM.sykmeldingsperioder
            gammelIM.egenmeldingsperioder shouldBe nyIM.agp?.egenmeldinger
            gammelIM.arbeidsgiverperioder shouldBe nyIM.agp?.perioder
            gammelIM.fullLønnIArbeidsgiverPerioden shouldBe
                FullLoennIAgpGammeltFormat(
                    false,
                    begrunnelse = BegrunnelseIngenEllerRedusertUtbetalingKodeGammeltFormat.FerieEllerAvspasering,
                    utbetalt = 300.3,
                )
            gammelIM.inntekt shouldBe nyIM.inntekt?.convert()
            gammelIM.inntektsdato shouldBe nyIM.inntekt?.inntektsdato
            gammelIM.bestemmendeFraværsdag.shouldBeNull() // settes alltid til 'null'
            gammelIM.naturalytelser shouldBe
                listOf(
                    NaturalytelseGammeltFormat(
                        naturalytelse = NaturalytelseKodeGammeltFormat.BEDRIFTSBARNEHAGEPLASS,
                        dato = 10.oktober,
                        beløp = 52.5,
                    ),
                    NaturalytelseGammeltFormat(
                        naturalytelse = NaturalytelseKodeGammeltFormat.BIL,
                        dato = 12.oktober,
                        beløp = 434.0,
                    ),
                )
            gammelIM.refusjon shouldBe nyIM.refusjon?.convert()
            gammelIM.innsenderNavn shouldBe nyIM.avsender.navn
            gammelIM.telefonnummer shouldBe nyIM.avsender.tlf
        }

        test("konverter inntekt fra nytt til gammelt IM-format") {
            val belop = 1000.0
            val dato = LocalDate.of(2024, 1, 1)
            val nyInntekt =
                Inntekt(
                    beloep = belop,
                    inntektsdato = dato,
                    naturalytelser = listOf(Naturalytelse(Naturalytelse.Kode.BEDRIFTSBARNEHAGEPLASS, belop, dato)),
                    endringAarsaker = listOf(Feilregistrert),
                )
            val gammelInntekt = nyInntekt.convert()
            gammelInntekt.beregnetInntekt shouldBe belop
            gammelInntekt.endringÅrsak shouldBe FeilregistrertGammeltFormat
            gammelInntekt.bekreftet shouldBe true
            gammelInntekt.manueltKorrigert shouldBe true

            val nyIM =
                mockInntektsmeldingV1().copy(
                    inntekt = nyInntekt,
                )
            val konvertert = nyIM.convert()
            konvertert.naturalytelser shouldBe listOf(NaturalytelseGammeltFormat(NaturalytelseKodeGammeltFormat.BEDRIFTSBARNEHAGEPLASS, dato, belop))
            konvertert.inntektsdato shouldBe dato
            konvertert.inntekt?.beregnetInntekt shouldBe belop
        }

        test("konverter reduksjon til V0") {
            val belop = 333.33
            val periode = listOf(10.september til 20.september)
            val egenmeldinger = listOf(10.september til 12.september)

            val nyIM =
                mockInntektsmeldingV1().copy(
                    agp =
                        Arbeidsgiverperiode(
                            periode,
                            egenmeldinger,
                            RedusertLoennIAgp(belop, RedusertLoennIAgp.Begrunnelse.FerieEllerAvspasering),
                        ),
                )

            val konvertert = nyIM.convert()
            konvertert.fullLønnIArbeidsgiverPerioden?.begrunnelse shouldBe BegrunnelseIngenEllerRedusertUtbetalingKodeGammeltFormat.FerieEllerAvspasering
            konvertert.fullLønnIArbeidsgiverPerioden?.utbetalerFullLønn shouldBe false
            konvertert.fullLønnIArbeidsgiverPerioden?.utbetalt shouldBe belop
            konvertert.arbeidsgiverperioder shouldBe periode
            konvertert.egenmeldingsperioder shouldBe egenmeldinger
        }

        test("konverter null-verdi for fullLønnIAGP") {
            val orginal =
                lagGammelInntektsmeldingMedTommeOgNullVerdier().copy(
                    fullLønnIArbeidsgiverPerioden = null,
                )

            val nyIM =
                mockInntektsmeldingV1().copy(
                    agp = orginal.convertAgp(),
                )

            val konvertert = nyIM.convert()
            konvertert.fullLønnIArbeidsgiverPerioden?.begrunnelse shouldBe null
            konvertert.fullLønnIArbeidsgiverPerioden?.utbetalerFullLønn shouldBe true
            konvertert.fullLønnIArbeidsgiverPerioden?.utbetalt shouldBe null
        }

        test("konverter refusjon til V0") {
            val belop = 123.45
            val dato1 = LocalDate.of(2023, 2, 2)
            val dato2 = LocalDate.of(2023, 2, 2)
            val refusjon =
                Refusjon(
                    belop,
                    listOf(
                        RefusjonEndring(belop, dato1),
                        RefusjonEndring(0.0, dato2),
                    ),
                )
            val gammelRefusjon = refusjon.convert()
            gammelRefusjon.refusjonEndringer shouldBe
                listOf(
                    RefusjonEndringGammeltFormat(belop, dato1),
                    RefusjonEndringGammeltFormat(0.0, dato2),
                )
            gammelRefusjon.refusjonOpphører shouldBe null
            gammelRefusjon.refusjonPrMnd shouldBe belop
        }

        test("konverter refusjon på gammelt format fjerner sluttdato og erstatter den med en endring med beløp 0 og startDato lik sluttDatoen") {
            val belop1 = 123.45
            val sluttDato = LocalDate.of(2023, 2, 28)
            val gammelRefusjon =
                RefusjonGammeltFormat(
                    utbetalerHeleEllerDeler = true,
                    refusjonPrMnd = belop1,
                    refusjonOpphører = sluttDato,
                    refusjonEndringer = emptyList(),
                )
            val refusjon = gammelRefusjon.convert()
            refusjon?.endringer[0]?.beloep shouldBe 0.0
            refusjon?.endringer[0]?.startdato shouldBe sluttDato
        }

        test("konverter refusjon på gammelt format med endringsliste fjerner duplikater") {
            val belop1 = 123.45
            val sluttDato = LocalDate.of(2023, 2, 28)
            val endretDato = sluttDato.minusDays(7)
            val gammelRefusjon =
                RefusjonGammeltFormat(
                    utbetalerHeleEllerDeler = true,
                    refusjonPrMnd = belop1,
                    refusjonOpphører = sluttDato,
                    refusjonEndringer =
                        listOf(
                            RefusjonEndringGammeltFormat(10.0, endretDato),
                            RefusjonEndringGammeltFormat(0.0, sluttDato),
                        ),
                )
            val refusjon = gammelRefusjon.convert()
            refusjon?.endringer?.size shouldBe 2
            refusjon?.endringer[0]?.beloep shouldBe 10.0
            refusjon?.endringer[0]?.startdato shouldBe endretDato
            refusjon?.endringer[1]?.beloep shouldBe 0.0
            refusjon?.endringer[1]?.startdato shouldBe sluttDato
        }
    })

private fun lagPeriode(): List<Periode> {
    val start = LocalDate.of(2023, 1, 1)
    return List(3) { Periode(start.plusDays(it.toLong()), start.plusDays(it.toLong())) }
}

private fun lagGammelInntektsmeldingMedTommeOgNullVerdier(): InntektsmeldingGammeltFormat =
    lagGammelInntektsmelding().copy(
        fraværsperioder = listOf(1.januar til 1.januar),
        egenmeldingsperioder = emptyList(),
        arbeidsgiverperioder = emptyList(),
        fullLønnIArbeidsgiverPerioden = null,
        inntekt = null,
        inntektsdato = null,
        bestemmendeFraværsdag = null,
        naturalytelser = null,
        innsenderNavn = null,
        telefonnummer = null,
    )

private fun lagGammelInntektsmelding(): InntektsmeldingGammeltFormat =
    InntektsmeldingGammeltFormat(
        orgnrUnderenhet = Orgnr.genererGyldig().verdi,
        identitetsnummer = Fnr.genererGyldig().verdi,
        egenmeldingsperioder =
            listOf(
                12.september til 13.september,
            ),
        fraværsperioder =
            listOf(
                14.september til 20.september,
                28.september til 21.oktober,
            ),
        arbeidsgiverperioder =
            listOf(
                12.september til 20.september,
                28.september til 4.oktober,
            ),
        inntektsdato = null,
        inntekt =
            InntektGammeltFormat(
                bekreftet = true,
                beregnetInntekt = 100.0,
                endringÅrsak = null,
                manueltKorrigert = false,
            ),
        fullLønnIArbeidsgiverPerioden = null,
        refusjon =
            RefusjonGammeltFormat(
                utbetalerHeleEllerDeler = true,
                refusjonPrMnd = 50.0,
                refusjonOpphører = LocalDate.EPOCH,
                refusjonEndringer = emptyList(),
            ),
        naturalytelser = null,
        innsenderNavn = "innsender",
        telefonnummer = "22222222",
        vedtaksperiodeId = UUID.randomUUID(),
    )
