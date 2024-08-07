package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.FullLoennIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntekt
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.joark.mappers.InntektDokumentTilSkjemainnholdMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mapstruct.factory.Mappers

class InntektDokumentTilSkjemainnholdMapperTest {
    private val inntektsmeldingDokument = mockInntektsmelding()
    private val mapper: InntektDokumentTilSkjemainnholdMapper = Mappers.getMapper(InntektDokumentTilSkjemainnholdMapper::class.java)

    @Test
    fun `skal mappe InntektsMeldingdokument til Skjema`() {
        val im = mapper.inntektDokumentTilInntekstmeldingM(inntektsmeldingDokument)
        val skjema = im.skjemainnhold
        assertNotNull(skjema.aarsakTilInnsending)
        assertNotNull(skjema.arbeidsgiver)
        assertEquals(inntektsmeldingDokument.orgnrUnderenhet, skjema.arbeidsgiver.virksomhetsnummer)
        assertEquals(inntektsmeldingDokument.telefonnummer, skjema.arbeidsgiver.kontaktinformasjon.telefonnummer)
        assertEquals(inntektsmeldingDokument.innsenderNavn, skjema.arbeidsgiver.kontaktinformasjon.kontaktinformasjonNavn)
        assertEquals(inntektsmeldingDokument.identitetsnummer, skjema.arbeidstakerFnr)
        assertEquals(inntektsmeldingDokument.bestemmendeFraværsdag, skjema.arbeidsforhold.foersteFravaersdag)
        assertNotNull(skjema.arbeidsforhold.beregnetInntekt)
        assertEquals(3, skjema.sykepengerIArbeidsgiverperioden.arbeidsgiverperiodeListe.size)
        assertNotNull(skjema.sykepengerIArbeidsgiverperioden.bruttoUtbetalt)
        assertEquals(
            inntektsmeldingDokument.fullLønnIArbeidsgiverPerioden!!.begrunnelse!!.name,
            skjema.sykepengerIArbeidsgiverperioden.begrunnelseForReduksjonEllerIkkeUtbetalt,
        )
        assertNotNull(skjema.refusjon.refusjonsbeloepPrMnd)
        assertNotNull(skjema.refusjon.refusjonsopphoersdato)
        assertEquals(3, skjema.refusjon.endringIRefusjonListe.size)
        assertEquals(2, skjema.opphoerAvNaturalytelseListe.size)
        assertNotNull(skjema.avsendersystem.innsendingstidspunkt)
        assertNotNull(skjema.arbeidsforhold.beregnetInntekt.aarsakVedEndring)
        println(xmlMapper().writeValueAsString(im))
    }

    @Test
    fun `skal mappe InntektsMeldingdokument til skjema også hvis begrunnelse er null`() {
        val im = mapper.inntektDokumentTilInntekstmeldingM(inntektsmeldingDokument.copy(fullLønnIArbeidsgiverPerioden = FullLoennIArbeidsgiverPerioden(false)))
        val skjema = im.skjemainnhold
        assertNotNull(skjema.aarsakTilInnsending)
        assertNotNull(skjema.arbeidsgiver)
        assertEquals(inntektsmeldingDokument.orgnrUnderenhet, skjema.arbeidsgiver.virksomhetsnummer)
        assertEquals(inntektsmeldingDokument.telefonnummer, skjema.arbeidsgiver.kontaktinformasjon.telefonnummer)
        assertEquals(inntektsmeldingDokument.innsenderNavn, skjema.arbeidsgiver.kontaktinformasjon.kontaktinformasjonNavn)
        assertEquals(inntektsmeldingDokument.identitetsnummer, skjema.arbeidstakerFnr)
        assertEquals(inntektsmeldingDokument.bestemmendeFraværsdag, skjema.arbeidsforhold.foersteFravaersdag)
        assertNotNull(skjema.arbeidsforhold.beregnetInntekt)
        assertEquals(3, skjema.sykepengerIArbeidsgiverperioden.arbeidsgiverperiodeListe.size)
        assertNull(skjema.sykepengerIArbeidsgiverperioden.bruttoUtbetalt)
        assertNull(skjema.sykepengerIArbeidsgiverperioden.begrunnelseForReduksjonEllerIkkeUtbetalt)
        assertNotNull(skjema.refusjon.refusjonsbeloepPrMnd)
        assertNotNull(skjema.refusjon.refusjonsopphoersdato)
        assertEquals(3, skjema.refusjon.endringIRefusjonListe.size)
        assertEquals(2, skjema.opphoerAvNaturalytelseListe.size)
        assertNotNull(skjema.avsendersystem.innsendingstidspunkt)
        assertNotNull(skjema.arbeidsforhold.beregnetInntekt.aarsakVedEndring)
        println(xmlMapper().writeValueAsString(im))
    }

    @Test
    fun `skal godta null-verdi i InntektEndringÅrsak`() {
        val inntektmeldingUtenAarsak =
            inntektsmeldingDokument.copy(
                inntekt =
                    Inntekt(
                        true,
                        1.0,
                        null,
                        false,
                    ),
            )
        val skjema = mapper.inntektDokumentTilSkjemaInnhold(inntektmeldingUtenAarsak)
        assertNull(skjema.arbeidsforhold.beregnetInntekt.aarsakVedEndring)
    }
}
