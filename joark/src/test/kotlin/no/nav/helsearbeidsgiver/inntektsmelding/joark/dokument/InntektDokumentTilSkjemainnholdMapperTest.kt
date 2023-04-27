package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.inntektsmelding.joark.mappers.InntektDokumentTilSkjemainnholdMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mapstruct.factory.Mappers

internal class InntektDokumentTilSkjemainnholdMapperTest {
    @Test
    fun `skall mappe InntektsMeldingdokument til Skjema`() {
        val inntektsmeldingDokument = MockInntektsmeldingDokument()
        val mapper = Mappers.getMapper(InntektDokumentTilSkjemainnholdMapper::class.java)

        val im = mapper.InntektDokumentTilInntekstmeldingM(inntektsmeldingDokument)
        val skjema = im.skjemainnhold

        assertNotNull(skjema.aarsakTilInnsending)
        assertNotNull(skjema.arbeidsgiver)
        assertEquals(skjema.arbeidsgiver.virksomhetsnummer, inntektsmeldingDokument.orgnrUnderenhet)
        assertEquals(skjema.arbeidstakerFnr, inntektsmeldingDokument.identitetsnummer)
        assertEquals(skjema.arbeidsforhold.foersteFravaersdag, inntektsmeldingDokument.bestemmendeFraværsdag)
        assertNotNull(skjema.arbeidsforhold.beregnetInntekt)
        assertEquals(skjema.sykepengerIArbeidsgiverperioden.arbeidsgiverperiodeListe.size, 3)
        assertNotNull(skjema.sykepengerIArbeidsgiverperioden.bruttoUtbetalt)
        assertNotNull(skjema.refusjon.refusjonsbeloepPrMnd)
        assertNotNull(skjema.refusjon.refusjonsopphoersdato)
        assertEquals(skjema.refusjon.endringIRefusjonListe.size, 3)
        assertEquals(skjema.opphoerAvNaturalytelseListe.size, 2)
        assertNotNull(skjema.avsendersystem.innsendingstidspunkt)
        assertNotNull(skjema.arbeidsforhold.beregnetInntekt.aarsakVedEndring)
        println(xmlMapper().writeValueAsString(im))
    }
}
