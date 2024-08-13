package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.mock

import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.BegrunnelseIngenEllerRedusertUtbetalingKode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.FullLoennIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Naturalytelse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.NaturalytelseKode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Refusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.domene.ForespoerselType
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtData
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.LocalDate
import java.util.UUID

fun mockInnsending(): Innsending =
    Innsending(
        orgnrUnderenhet = Orgnr.genererGyldig().verdi,
        identitetsnummer = Fnr.genererGyldig().verdi,
        behandlingsdager = listOf(LocalDate.now().plusDays(5)),
        egenmeldingsperioder =
            listOf(
                Periode(
                    fom = LocalDate.now(),
                    tom = LocalDate.now().plusDays(2),
                ),
            ),
        arbeidsgiverperioder = emptyList(),
        bestemmendeFraværsdag = LocalDate.now(),
        fraværsperioder =
            listOf(
                Periode(
                    fom = LocalDate.now().plusDays(3),
                    tom = LocalDate.now().plusDays(24),
                ),
            ),
        inntekt =
            Inntekt(
                bekreftet = true,
                beregnetInntekt = 32100.0,
                endringÅrsak = null,
                manueltKorrigert = false,
            ),
        fullLønnIArbeidsgiverPerioden =
            FullLoennIArbeidsgiverPerioden(
                utbetalerFullLønn = true,
                begrunnelse = BegrunnelseIngenEllerRedusertUtbetalingKode.ArbeidOpphoert,
            ),
        refusjon =
            Refusjon(
                utbetalerHeleEllerDeler = true,
                refusjonPrMnd = 200.0,
                refusjonOpphører = LocalDate.now(),
            ),
        naturalytelser =
            listOf(
                Naturalytelse(
                    naturalytelse = NaturalytelseKode.KOSTDOEGN,
                    dato = LocalDate.now(),
                    beløp = 300.0,
                ),
            ),
        årsakInnsending = AarsakInnsending.ENDRING,
        bekreftOpplysninger = true,
    )

fun mockForespoerselSvarSuksess(): ForespoerselSvar.Suksess {
    val orgnr = Orgnr.genererGyldig().verdi
    return ForespoerselSvar.Suksess(
        type = ForespoerselType.KOMPLETT,
        orgnr = orgnr,
        fnr = Fnr.genererGyldig().verdi,
        vedtaksperiodeId = UUID.randomUUID(),
        egenmeldingsperioder = listOf(1.januar til 1.januar),
        sykmeldingsperioder = listOf(2.januar til 16.januar),
        skjaeringstidspunkt = 11.januar,
        bestemmendeFravaersdager =
            mapOf(
                orgnr to 1.januar,
                "343999567" to 11.januar,
            ),
        forespurtData = mockForespurtData(),
        erBesvart = false,
    )
}
