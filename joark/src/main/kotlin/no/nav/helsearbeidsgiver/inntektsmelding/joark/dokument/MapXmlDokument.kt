package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

fun mapXmlDokument(inntektsmeldingDokument: InntektsmeldingDokument): String {
    val kontaktNavn = "Ukjent kontaktperson" // TODO
    val kontaktTelefon = "" // TODO
    return """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<melding xmlns="http://seres.no/xsd/NAV/Inntektsmelding_M/20180924">
    <Skjemainnhold>
        <ytelse>Sykepenger</ytelse>
        <aarsakTilInnsending>${inntektsmeldingDokument.årsakInnsending}</aarsakTilInnsending>
        <arbeidsgiver>
            <virksomhetsnummer>${inntektsmeldingDokument.orgnrUnderenhet}</virksomhetsnummer>
            <kontaktinformasjon>
                <kontaktinformasjonNavn>$kontaktNavn</kontaktinformasjonNavn>
                <telefonnummer>$kontaktTelefon</telefonnummer>
            </kontaktinformasjon>
        </arbeidsgiver>
        <arbeidstakerFnr>${inntektsmeldingDokument.identitetsnummer}</arbeidstakerFnr>
        <naerRelasjon>false</naerRelasjon>
        <arbeidsforhold>
            <foersteFravaersdag>${inntektsmeldingDokument.bestemmendeFraværsdag}</foersteFravaersdag>
            <beregnetInntekt>
                <beloep>${inntektsmeldingDokument.beregnetInntekt}</beloep>
            </beregnetInntekt>
        </arbeidsforhold>
        <sykepengerIArbeidsgiverperioden>
            <arbeidsgiverperiodeListe>
            {${inntektsmeldingDokument.arbeidsgiverperioder.map {
        """
                        <arbeidsgiverperiode>
                            <fom>${it.fom}</fom>
                            <tom>${it.tom}</tom>
                        </arbeidsgiverperiode>
        """.trimIndent()
    }}}
            </arbeidsgiverperiodeListe>
            <bruttoUtbetalt>0</bruttoUtbetalt>
            <begrunnelseForReduksjonEllerIkkeUtbetalt>${inntektsmeldingDokument.beregnetInntektEndringÅrsak}</begrunnelseForReduksjonEllerIkkeUtbetalt>
        </sykepengerIArbeidsgiverperioden>
        <refusjon>
            <refusjonsbeloepPrMnd>${inntektsmeldingDokument.refusjon.refusjonPrMnd}</refusjonsbeloepPrMnd>
            <refusjonsopphoersdato>${inntektsmeldingDokument.refusjon.refusjonOpphører}</refusjonsopphoersdato>
        </refusjon>
        <gjenopptakelseNaturalytelseListe>
        </gjenopptakelseNaturalytelseListe>
        <opphoerAvNaturalytelseListe>
            {${inntektsmeldingDokument.naturalytelser?.map {
        """
            <opphoerAvNaturalytelse>
                <naturalytelseType>${it.naturalytelse}</naturalytelseType>
                <fom>${it.dato}</fom>
                <beloepPrMnd>${it.beløp}</beloepPrMnd>
            </opphoerAvNaturalytelse>
        """.trimIndent()
    }}}
        </opphoerAvNaturalytelseListe>
        <innsendingstidspunkt>${inntektsmeldingDokument.tidspunkt}</innsendingstidspunkt>
        <avsendersystem>
            <systemnavn>NAV_NO</systemnavn>
            <systemversjon>1.0</systemversjon>
        </avsendersystem>
    </Skjemainnhold>
</melding>
""".trim()
}
