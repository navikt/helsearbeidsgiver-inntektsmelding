package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

fun mapXmlDokument(inntektsmeldingDokument: InntektsmeldingDokument): String {
    return """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<melding xmlns="http://seres.no/xsd/NAV/Inntektsmelding_M/20180924">
    <Skjemainnhold>
        <ytelse>Sykepenger</ytelse>
        <aarsakTilInnsending>Ny</aarsakTilInnsending>
        <arbeidsgiver>
            <virksomhetsnummer>${inntektsmeldingDokument.orgnrUnderenhet}</virksomhetsnummer>
            <kontaktinformasjon>
                <kontaktinformasjonNavn>Fru Corporate</kontaktinformasjonNavn>
                <telefonnummer>01010101</telefonnummer>
            </kontaktinformasjon>
        </arbeidsgiver>
        <arbeidstakerFnr>${inntektsmeldingDokument.identitetsnummer}</arbeidstakerFnr>
        <naerRelasjon>false</naerRelasjon>
        <arbeidsforhold>
            <foersteFravaersdag>2018-10-26</foersteFravaersdag>
            <beregnetInntekt>
                <beloep>${inntektsmeldingDokument.bruttoInntekt}</beloep>
            </beregnetInntekt>
        </arbeidsforhold>
        <sykepengerIArbeidsgiverperioden>
            <arbeidsgiverperiodeListe>
                <arbeidsgiverperiode>
                    <fom>2018-01-01</fom>
                    <tom>2018-01-10</tom>
                </arbeidsgiverperiode>
            </arbeidsgiverperiodeListe>
            <bruttoUtbetalt>5000</bruttoUtbetalt>
        </sykepengerIArbeidsgiverperioden>
        <opphoerAvNaturalytelseListe>
            <opphoerAvNaturalytelse>
                <naturalytelseType>elektroniskKommunikasjon</naturalytelseType>
                <fom>2018-03-02</fom>
                <beloepPrMnd>100</beloepPrMnd>
            </opphoerAvNaturalytelse>
        </opphoerAvNaturalytelseListe>
        <avsendersystem>
            <systemnavn>NAV_NO</systemnavn>
            <systemversjon>1.0</systemversjon>
        </avsendersystem>
    </Skjemainnhold>
</melding>
""".trim()
}
