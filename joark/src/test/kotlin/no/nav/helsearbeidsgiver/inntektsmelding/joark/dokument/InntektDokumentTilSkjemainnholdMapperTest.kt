package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Inntekt
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
