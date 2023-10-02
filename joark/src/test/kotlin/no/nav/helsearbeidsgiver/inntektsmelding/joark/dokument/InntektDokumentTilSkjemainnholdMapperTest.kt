package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.FullLonnIArbeidsgiverPerioden
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Inntekt
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingDokument
import no.nav.helsearbeidsgiver.inntektsmelding.joark.mappers.InntektDokumentTilSkjemainnholdMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mapstruct.factory.Mappers
import java.math.BigDecimal

class InntektDokumentTilSkjemainnholdMapperTest {

    private val inntektsmeldingDokument = mockInntektsmeldingDokument()
    private val mapper: InntektDokumentTilSkjemainnholdMapper = Mappers.getMapper(InntektDokumentTilSkjemainnholdMapper::class.java)

    @Test
    fun `skal mappe InntektsMeldingdokument til Skjema`() {
        val im = mapper.InntektDokumentTilInntekstmeldingM(inntektsmeldingDokument)
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
        assertEquals(inntektsmeldingDokument.fullLønnIArbeidsgiverPerioden!!.begrunnelse!!.value, skjema.sykepengerIArbeidsgiverperioden.begrunnelseForReduksjonEllerIkkeUtbetalt)
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
        val im = mapper.InntektDokumentTilInntekstmeldingM(inntektsmeldingDokument.copy(fullLønnIArbeidsgiverPerioden = FullLonnIArbeidsgiverPerioden(false)))
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
        println(xmlMapper().writeValueAsString(im))    }

    @Test
    fun `skal godta null-verdi i InntektEndringÅrsak`() {
        val inntektmeldingUtenAarsak =
            inntektsmeldingDokument.copy(
                inntekt = Inntekt(
                    true,
                    BigDecimal.ONE,
                    null,
                    false
                )
            )
        val skjema = mapper.inntektDokumentTilSkjemaInnhold(inntektmeldingUtenAarsak)
        assertNull(skjema.arbeidsforhold.beregnetInntekt.aarsakVedEndring)
    }
}
