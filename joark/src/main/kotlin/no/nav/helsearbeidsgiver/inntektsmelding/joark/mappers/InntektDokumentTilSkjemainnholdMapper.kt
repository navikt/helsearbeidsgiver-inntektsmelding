package no.nav.helsearbeidsgiver.inntektsmelding.joark.mappers

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Naturalytelse
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Refusjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.RefusjonEndring
import no.seres.xsd.nav.inntektsmelding_m._20181211.EndringIRefusjon
import no.seres.xsd.nav.inntektsmelding_m._20181211.InntektsmeldingM
import no.seres.xsd.nav.inntektsmelding_m._20181211.NaturalytelseDetaljer
import no.seres.xsd.nav.inntektsmelding_m._20181211.Skjemainnhold
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.Mappings

@Mapper(uses = [DateMapper::class, InntektEndringAarsakMapper::class, ArbeidsgiverperiodeListeMapper::class])
interface InntektDokumentTilSkjemainnholdMapper {

    @Mapping(source = ".", target = "skjemainnhold")
    fun InntektDokumentTilInntekstmeldingM(inntektsmeldingDokument: InntektsmeldingDokument): InntektsmeldingM

    @Mappings(
        Mapping(constant = "Sykepenger", target = "ytelse"),
        Mapping(constant = ".", target = "arbeidsgiver.kontaktinformasjon.kontaktinformasjonNavn"),
        Mapping(constant = ".", target = "arbeidsgiver.kontaktinformasjon.telefonnummer"),
        Mapping(source = "årsakInnsending.value", target = "aarsakTilInnsending"),
        Mapping(source = "orgnrUnderenhet", target = "arbeidsgiver.virksomhetsnummer"),
        Mapping(source = "identitetsnummer", target = "arbeidstakerFnr"),
        Mapping(source = "bestemmendeFraværsdag", target = "arbeidsforhold.foersteFravaersdag"),
        Mapping(source = "beregnetInntekt", target = "arbeidsforhold.beregnetInntekt.beloep"),
        Mapping(source = "inntekt.endringÅrsak", target = "arbeidsforhold.beregnetInntekt.aarsakVedEndring"),
        Mapping(source = "arbeidsgiverperioder", target = "sykepengerIArbeidsgiverperioden.arbeidsgiverperiodeListe"),
        Mapping(source = "fullLønnIArbeidsgiverPerioden.utbetalt", target = "sykepengerIArbeidsgiverperioden.bruttoUtbetalt"),
        Mapping(source = "refusjon", target = "refusjon"),
        Mapping(source = "naturalytelser", target = "opphoerAvNaturalytelseListe"),
        Mapping(constant = "NAV_NO", target = "avsendersystem.systemnavn"),
        Mapping(constant = "1.0", target = "avsendersystem.systemversjon"),
        Mapping(source = "tidspunkt", target = "avsendersystem.innsendingstidspunkt")
    )
    fun inntektDokumentTilSkjemaInnhold(inntektsmeldingDokument: InntektsmeldingDokument): Skjemainnhold

    @Mappings(
        Mapping(source = "refusjonPrMnd", target = "refusjonsbeloepPrMnd"),
        Mapping(source = "refusjonOpphører", target = "refusjonsopphoersdato"),
        Mapping(source = "refusjonEndringer", target = "endringIRefusjonListe")
    )
    fun mapRefusjon(refusjon: Refusjon): no.seres.xsd.nav.inntektsmelding_m._20181211.Refusjon

    @Mappings(
        Mapping(source = "dato", target = "endringsdato"),
        Mapping(source = "beløp", target = "refusjonsbeloepPrMnd")
    )
    fun mapEndringIRefusjon(refusjonEndring: RefusjonEndring): EndringIRefusjon

    @Mappings(
        Mapping(source = "beløp", target = "beloepPrMnd"),
        Mapping(source = "dato", target = "fom"),
        Mapping(source = "naturalytelse.value", target = "naturalytelseType")
    )
    fun mapNaturalytelseDetaljer(naturalytelse: Naturalytelse): NaturalytelseDetaljer
}
