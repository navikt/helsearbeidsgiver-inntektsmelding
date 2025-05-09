package no.nav.helsearbeidsgiver.inntektsmelding.joark

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Bonus
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Feilregistrert
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Ferie
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Tariffendring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.VarigLoennsendring
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.utils.test.date.november
import no.nav.helsearbeidsgiver.utils.test.date.oktober
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TilXmlInntektsmeldingTest {
    @Test
    fun `skal mappe inntektsmelding til xml-skjema`() {
        val im = mockInntektsmeldingV1()
        val imXml = tilXmlInntektsmelding(im)
        val skjema = imXml.skjemainnhold
        Assertions.assertNotNull(skjema.aarsakTilInnsending)
        Assertions.assertNotNull(skjema.arbeidsgiver)
        Assertions.assertEquals(im.avsender.orgnr.verdi, skjema.arbeidsgiver.virksomhetsnummer)
        Assertions.assertEquals(im.avsender.tlf, skjema.arbeidsgiver.kontaktinformasjon.telefonnummer)
        Assertions.assertEquals(im.avsender.navn, skjema.arbeidsgiver.kontaktinformasjon.kontaktinformasjonNavn)
        Assertions.assertEquals(im.sykmeldt.fnr.verdi, skjema.arbeidstakerFnr)
        Assertions.assertEquals(20.oktober, skjema.arbeidsforhold.foersteFravaersdag)
        Assertions.assertNotNull(skjema.arbeidsforhold.beregnetInntekt)
        Assertions.assertEquals(2, skjema.sykepengerIArbeidsgiverperioden.arbeidsgiverperiodeListe.size)
        Assertions.assertNotNull(skjema.sykepengerIArbeidsgiverperioden.bruttoUtbetalt)
        Assertions.assertEquals(
            im.agp
                ?.redusertLoennIAgp
                ?.begrunnelse
                ?.name!!,
            skjema.sykepengerIArbeidsgiverperioden.begrunnelseForReduksjonEllerIkkeUtbetalt,
        )
        Assertions.assertNotNull(skjema.refusjon.refusjonsbeloepPrMnd)
        Assertions.assertEquals(4, skjema.refusjon.endringIRefusjonListe.size)
        Assertions.assertEquals(30.november, skjema.refusjon.refusjonsopphoersdato)
        Assertions.assertEquals(2, skjema.opphoerAvNaturalytelseListe.size)
        Assertions.assertNotNull(skjema.avsendersystem.innsendingstidspunkt)
        Assertions.assertNotNull(skjema.arbeidsforhold.beregnetInntekt.aarsakVedEndring)
        Assertions.assertEquals(
            im.inntekt
                ?.endringAarsaker
                ?.firstOrNull()
                ?.tilTekst(),
            skjema.arbeidsforhold.beregnetInntekt.aarsakVedEndring,
        )
        println(xmlMapper().writeValueAsString(imXml))
    }

    @Test
    fun `skal godta null-verdi i InntektEndringÅrsak`() {
        val inntektmeldingUtenAarsak =
            mockInntektsmeldingV1().copy(
                inntekt =
                    Inntekt(
                        beloep = 1.0,
                        inntektsdato = 20.oktober,
                        naturalytelser = emptyList(),
                        endringAarsaker = emptyList(),
                    ),
            )
        val im = tilXmlInntektsmelding(inntektmeldingUtenAarsak)
        Assertions.assertNull(im.skjemainnhold.arbeidsforhold.beregnetInntekt.aarsakVedEndring)
    }

    @Test
    fun `årsak til inntektsendring mappes korrekt`() {
        val gjelderFra = LocalDate.of(2020, 1, 1)
        val gjelderTil = LocalDate.of(2020, 1, 3)
        val tariffendring = Tariffendring(gjelderFra, gjelderFra)
        Assertions.assertEquals("Tariffendring: fra $gjelderFra", tariffendring.tilTekst())

        val feriePerioder = listOf(Periode(gjelderFra, gjelderTil), Periode(gjelderFra, gjelderTil))
        val ferie = Ferie(feriePerioder)
        val ferieString = ferie.tilTekst()
        Assertions.assertEquals("Ferie: fra $gjelderFra til $gjelderTil, fra $gjelderFra til $gjelderTil", ferieString)

        val varigLonnsendring = VarigLoennsendring(gjelderFra)
        Assertions.assertEquals("Varig lønnsendring: fra $gjelderFra", varigLonnsendring.tilTekst())

        Assertions.assertEquals("Bonus", Bonus.tilTekst())

        Assertions.assertEquals("Mangelfull eller uriktig rapportering til A-ordningen", Feilregistrert.tilTekst())
    }
}

private fun xmlMapper(): ObjectMapper =
    XmlMapper().apply {
        registerModule(JaxbAnnotationModule())
        registerModule(JavaTimeModule())
        configure(FromXmlParser.Feature.EMPTY_ELEMENT_AS_NULL, true)
        enable(SerializationFeature.INDENT_OUTPUT)
    }
