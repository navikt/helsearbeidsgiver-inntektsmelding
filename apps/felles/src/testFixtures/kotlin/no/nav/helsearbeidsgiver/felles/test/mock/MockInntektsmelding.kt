package no.nav.helsearbeidsgiver.felles.test.mock

import no.nav.helsearbeidsgiver.domene.inntektsmelding.Utils.convert
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Arbeidsgiverperiode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Avsender
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Naturalytelse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.NyStillingsprosent
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.RedusertLoennIAgp
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Refusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.RefusjonEndring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Sykmeldt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaAvsender
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmeldingSelvbestemt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.domene.EksternInntektsmelding
import no.nav.helsearbeidsgiver.utils.test.date.kl
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.date.november
import no.nav.helsearbeidsgiver.utils.test.date.oktober
import no.nav.helsearbeidsgiver.utils.test.date.september
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.ZoneOffset
import java.util.UUID
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding as InntektsmeldingGammeltFormat

fun mockSkjemaInntektsmelding(): SkjemaInntektsmelding {
    val inntektsmelding = mockInntektsmeldingV1()
    return SkjemaInntektsmelding(
        forespoerselId = inntektsmelding.type.id,
        avsenderTlf = inntektsmelding.avsender.tlf,
        agp = inntektsmelding.agp,
        inntekt = inntektsmelding.inntekt,
        refusjon = inntektsmelding.refusjon,
    )
}

fun mockSkjemaInntektsmeldingSelvbestemt(): SkjemaInntektsmeldingSelvbestemt {
    val inntektsmelding = mockInntektsmeldingV1()
    return SkjemaInntektsmeldingSelvbestemt(
        selvbestemtId = UUID.randomUUID(),
        sykmeldtFnr = inntektsmelding.sykmeldt.fnr,
        avsender =
            SkjemaAvsender(
                orgnr = inntektsmelding.avsender.orgnr,
                tlf = inntektsmelding.avsender.tlf,
            ),
        sykmeldingsperioder = inntektsmelding.sykmeldingsperioder,
        agp = inntektsmelding.agp,
        inntekt = inntektsmelding.inntekt!!,
        refusjon = inntektsmelding.refusjon,
        vedtaksperiodeId = UUID.randomUUID(),
    )
}

fun mockInntektsmeldingV1(): Inntektsmelding =
    Inntektsmelding(
        id = UUID.randomUUID(),
        type =
            Inntektsmelding.Type.Forespurt(
                id = UUID.randomUUID(),
            ),
        sykmeldt =
            Sykmeldt(
                fnr = Fnr.genererGyldig(),
                navn = "Skummel Bolle",
            ),
        avsender =
            Avsender(
                orgnr = Orgnr.genererGyldig(),
                orgNavn = "Skumle bakverk A/S",
                navn = "Nifs Krumkake",
                tlf = randomDigitString(8),
            ),
        sykmeldingsperioder =
            listOf(
                5.oktober til 15.oktober,
                20.oktober til 3.november,
            ),
        agp = mockArbeidsgiverperiode(),
        inntekt = mockInntekt(),
        refusjon = mockRefusjon(),
        aarsakInnsending = AarsakInnsending.Endring,
        mottatt = 14.mars.kl(14, 41, 42, 0).atOffset(ZoneOffset.ofHours(1)),
        vedtaksperiodeId = UUID.randomUUID(),
    )

fun mockArbeidsgiverperiode(): Arbeidsgiverperiode =
    Arbeidsgiverperiode(
        perioder =
            listOf(
                5.oktober til 15.oktober,
                20.oktober til 22.oktober,
            ),
        egenmeldinger =
            listOf(
                28.september til 28.september,
                30.september til 30.september,
            ),
        redusertLoennIAgp =
            RedusertLoennIAgp(
                beloep = 300.3,
                begrunnelse = RedusertLoennIAgp.Begrunnelse.FerieEllerAvspasering,
            ),
    )

fun mockInntekt(): Inntekt =
    Inntekt(
        beloep = 544.6,
        inntektsdato = 28.september,
        naturalytelser =
            listOf(
                Naturalytelse(
                    naturalytelse = Naturalytelse.Kode.BEDRIFTSBARNEHAGEPLASS,
                    verdiBeloep = 52.5,
                    sluttdato = 10.oktober,
                ),
                Naturalytelse(
                    naturalytelse = Naturalytelse.Kode.BIL,
                    verdiBeloep = 434.0,
                    sluttdato = 12.oktober,
                ),
            ),
        endringAarsak =
            NyStillingsprosent(
                gjelderFra = 16.oktober,
            ),
        endringAarsaker =
            listOf(
                NyStillingsprosent(
                    gjelderFra = 16.oktober,
                ),
            ),
    )

fun mockRefusjon(): Refusjon =
    Refusjon(
        beloepPerMaaned = 150.2,
        endringer =
            listOf(
                RefusjonEndring(
                    beloep = 140.9,
                    startdato = 1.november,
                ),
                RefusjonEndring(
                    beloep = 130.8,
                    startdato = 18.november,
                ),
                RefusjonEndring(
                    beloep = 120.7,
                    startdato = 21.november,
                ),
            ),
        sluttdato = 30.november,
    )

fun mockInntektsmeldingGammeltFormat(): InntektsmeldingGammeltFormat = mockInntektsmeldingV1().convert()

fun mockEksternInntektsmelding(): EksternInntektsmelding =
    EksternInntektsmelding(
        avsenderSystemNavn = "Trygge Trygves Trygdesystem",
        avsenderSystemVersjon = "T1000",
        arkivreferanse = "Arkiv nr. 49",
        tidspunkt = 12.oktober.kl(14, 0, 12, 0),
    )
