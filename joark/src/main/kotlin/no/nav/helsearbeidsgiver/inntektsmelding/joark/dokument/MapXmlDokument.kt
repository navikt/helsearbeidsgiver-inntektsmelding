package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

fun mapXmlDokument(inntektsmeldingDokument: InntektsmeldingDokument): String {
    return """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<melding xmlns="http://seres.no/xsd/NAV/Inntektsmelding_M/20180924">
    <Skjemainnhold>
        <ytelse>Sykepenger</ytelse>
        <aarsakTilInnsending>${inntektsmeldingDokument.årsakInnsending}</aarsakTilInnsending>
        <arbeidsgiver>
            <virksomhetsnummer>${inntektsmeldingDokument.orgnrUnderenhet}</virksomhetsnummer>
            <kontaktinformasjon>
                <kontaktinformasjonNavn>Ukjent kontaktperson</kontaktinformasjonNavn>
                <telefonnummer>Ukjent telefon</telefonnummer>
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
        <refusjon>
            <refusjonsbeloepPrMnd>${inntektsmeldingDokument.refusjon.refusjonPrMnd}</refusjonsbeloepPrMnd>
            <refusjonsopphoersdato>${inntektsmeldingDokument.refusjon.refusjonOpphører}</refusjonsopphoersdato>
        </refusjon>
        <avsendersystem>
            <systemnavn>NAV_NO</systemnavn>
            <systemversjon>1.0</systemversjon>
        </avsendersystem>
    </Skjemainnhold>
</melding>
""".trim()
}
