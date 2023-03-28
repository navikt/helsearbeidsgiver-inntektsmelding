package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.RefusjonEndring

fun mapXmlDokument(inntektsmeldingDokument: no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument): String {
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
                ${mapArbeidsgiverperioder(inntektsmeldingDokument.arbeidsgiverperioder)}
            </arbeidsgiverperiodeListe>
            <bruttoUtbetalt>${inntektsmeldingDokument.fullLønnIArbeidsgiverPerioden.utbetalt}</bruttoUtbetalt>
            <begrunnelseForReduksjonEllerIkkeUtbetalt></begrunnelseForReduksjonEllerIkkeUtbetalt>
        </sykepengerIArbeidsgiverperioden>
        <refusjon>
            <refusjonsbeloepPrMnd>${inntektsmeldingDokument.refusjon.refusjonPrMnd}</refusjonsbeloepPrMnd>
            <refusjonsopphoersdato>${inntektsmeldingDokument.refusjon.refusjonOpphører}</refusjonsopphoersdato>
            <endringIRefusjonListe>
                ${mapRefusjonsEndringer(inntektsmeldingDokument.refusjon.refusjonEndringer)}
            </endringIRefusjonListe>
        </refusjon>
        <gjenopptakelseNaturalytelseListe>
        </gjenopptakelseNaturalytelseListe>
        <opphoerAvNaturalytelseListe>
            ${mapNaturalytelser(inntektsmeldingDokument.naturalytelser)}
        </opphoerAvNaturalytelseListe>

        <avsendersystem>
            <systemnavn>NAV_NO</systemnavn>
            <systemversjon>1.0</systemversjon>
            <innsendingstidspunkt>${inntektsmeldingDokument.tidspunkt}</innsendingstidspunkt>
        </avsendersystem>
    </Skjemainnhold>
</melding>
""".trim()
}

fun mapArbeidsgiverperioder(arbeidsgiverperioder: List<Periode>): String = arbeidsgiverperioder.map {
    "<arbeidsgiverperiode>" +
        "<fom>${it.fom}</fom>" +
        "<tom>${it.tom}</tom>" +
        "</arbeidsgiverperiode>"
}.joinToString("\n")

fun mapRefusjonsEndringer(refusjonEndringer: List<RefusjonEndring>? = null): String = refusjonEndringer?.map {
    "<endringIRefusjon>" +
        "<endringsdato>${it.dato}</endringsdato>" +
        "<refusjonsbeloepPrMnd>${it.beløp}</refusjonsbeloepPrMnd>" +
        "</endringIRefusjon>"
}?.joinToString("\n") ?: ""

fun mapNaturalytelser(naturalytelser: List<no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Naturalytelse>? = null): String = naturalytelser?.map {
    "<opphoerAvNaturalytelse>" +
        "<naturalytelseType>${it.naturalytelse.value}</naturalytelseType>" +
        "<fom>${it.dato}</fom><beloepPrMnd>${it.beløp}</beloepPrMnd>" +
        "</opphoerAvNaturalytelse>"
}?.joinToString("\n") ?: ""
