package no.nav.helsearbeidsgiver.inntektsmelding.joark.mappers

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InntektsmeldingDokument
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Naturalytelse
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Refusjon
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.RefusjonEndring
import no.seres.xsd.nav.inntektsmelding_m._20181211.EndringIRefusjon
import no.seres.xsd.nav.inntektsmelding_m._20181211.InntektsmeldingM
import no.seres.xsd.nav.inntektsmelding_m._20181211.NaturalytelseDetaljer
import no.seres.xsd.nav.inntektsmelding_m._20181211.Skjemainnhold
import org.mapstruct.AfterMapping
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget
import org.mapstruct.Mappings

@Mapper(uses = arrayOf(DateMapper::class, InntektEndringAarsakMapper::class))
abstract class InntektDokumentTilSkjemainnholdMapper {

    @Mapping(source = ".", target = "skjemainnhold")
    abstract fun InntektDokumentTilInntekstmeldingM(inntektsmeldingDokument: InntektsmeldingDokument): InntektsmeldingM

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
    abstract fun inntektDokumentTilSkjemaInnhold(inntektsmeldingDokument: InntektsmeldingDokument): Skjemainnhold

    @AfterMapping
    fun after(@MappingTarget skjemainnhold: Skjemainnhold) {
        // Dersom man kan garantere at man ikke får tomme lister når felt ikke er fylt ut, ville ikke dette være nødvendig.
        // så kanskje finnes det andre måter å løse dette på
        if (skjemainnhold.sykepengerIArbeidsgiverperioden.arbeidsgiverperiodeListe.isEmpty()) {
            skjemainnhold.sykepengerIArbeidsgiverperioden.arbeidsgiverperiodeListe = null
        }
    }

    @Mappings(
        Mapping(source = "refusjonPrMnd", target = "refusjonsbeloepPrMnd"),
        Mapping(source = "refusjonOpphører", target = "refusjonsopphoersdato"),
        Mapping(source = "refusjonEndringer", target = "endringIRefusjonListe")
    )
    abstract fun mapRefusjon(refusjon: Refusjon): no.seres.xsd.nav.inntektsmelding_m._20181211.Refusjon

    @Mappings(
        Mapping(source = "dato", target = "endringsdato"),
        Mapping(source = "beløp", target = "refusjonsbeloepPrMnd")
    )
    abstract fun mapEndringIRefusjon(refusjonEndring: RefusjonEndring): EndringIRefusjon

    @Mappings(
        Mapping(source = "beløp", target = "beloepPrMnd"),
        Mapping(source = "dato", target = "fom"),
        Mapping(source = "naturalytelse.value", target = "naturalytelseType")
    )
    abstract fun mapNaturalytelseDetaljer(naturalytelse: Naturalytelse): NaturalytelseDetaljer
}
