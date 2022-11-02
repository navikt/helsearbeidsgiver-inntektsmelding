package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.inntektsmelding.joark.Inntektsmelding

fun mapXmlDokument(inntektsmelding: Inntektsmelding): String {
    return """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<melding xmlns="http://seres.no/xsd/NAV/Inntektsmelding_M/20180924">
    <Skjemainnhold>
        <ytelse>Sykepenger</ytelse>
        <aarsakTilInnsending>Ny</aarsakTilInnsending>
        <arbeidsgiver>
            <virksomhetsnummer>${inntektsmelding.orgnrUnderenhet}</virksomhetsnummer>
        </arbeidsgiver>
        <arbeidstakerFnr>${inntektsmelding.identitetsnummer}</arbeidstakerFnr>
        <naerRelasjon>false</naerRelasjon>
        <arbeidsforhold>
            <foersteFravaersdag>2018-10-26</foersteFravaersdag>
            <beregnetInntekt>
                <beloep>${inntektsmelding.bruttonInntekt}</beloep>
            </beregnetInntekt>
        </arbeidsforhold>
        <avsendersystem>
            <systemnavn>nav.no</systemnavn>
            <systemversjon>1.0</systemversjon>
        </avsendersystem>
        <omsorgspenger>
            <fravaersPerioder>
                <fravaerPeriode>
                    <fom>2018-01-01</fom>
                    <tom>2018-01-10</tom>
                </fravaerPeriode>
            </fravaersPerioder>
        </omsorgspenger>
    </Skjemainnhold>
</melding>""".trim()
}
