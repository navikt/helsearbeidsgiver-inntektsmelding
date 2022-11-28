package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helsearbeidsgiver.inntektsmelding.joark.INNTEKTMELDING_REQUEST
import no.nav.helsearbeidsgiver.inntektsmelding.joark.INNTEKTMELDING_REQUEST_FNUTT
import no.nav.helsearbeidsgiver.inntektsmelding.joark.INNTEKTMELDING_REQUEST_OPTIONALS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

internal class MapInntektsmeldingDokumentKtTest {

    internal val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(JavaTimeModule())

    val fulltNavn = "Kari Normann"
    val arbeidsgiver = "Norge AS"

    @Test
    fun skal_kaste_exception_ved_feil() {
        val packet = mapOf(
            "identitetsnummer" to ""
        )
        assertThrows<UgyldigFormatException> {
            mapInntektsmeldingDokument(objectMapper.readTree(objectMapper.writeValueAsString(packet)), fulltNavn, arbeidsgiver)
        }
    }

    @Test
    fun skal_mappe_alle_data() {
        val im = mapInntektsmeldingDokument(objectMapper.readTree(objectMapper.writeValueAsString(INNTEKTMELDING_REQUEST)), fulltNavn, arbeidsgiver)
        assertEquals("123", im.identitetsnummer)
        assertEquals("abc", im.orgnrUnderenhet)
        assertEquals(listOf(LocalDate.of(2022, 10, 27), LocalDate.of(2022, 10, 26)), im.behandlingsdager)
        assertEquals(
            listOf(
                Periode(LocalDate.of(2022, 9, 1), LocalDate.of(2022, 9, 5)),
                Periode(LocalDate.of(2022, 9, 6), LocalDate.of(2022, 9, 15))
            ),
            im.egenmeldingsperioder,
            "Egenmeldingsperioder"
        )
        assertEquals(
            listOf(
                Periode(LocalDate.of(2022, 8, 1), LocalDate.of(2022, 8, 2)),
                Periode(LocalDate.of(2022, 8, 3), LocalDate.of(2022, 8, 4))
            ),
            im.arbeidsgiverperioder,
            "Arbeidsgiverperioder"
        )
        assertEquals(LocalDate.of(2022, 9, 5), im.bestemmendeFraværsdag, "bestemmendeFraværsdag")
        assertEquals(
            listOf(
                Periode(LocalDate.of(2022, 7, 1), LocalDate.of(2022, 7, 2)),
                Periode(LocalDate.of(2022, 7, 3), LocalDate.of(2022, 7, 4))
            ),
            im.fraværsperioder,
            "Fraværsperioder"
        )
        // Inntekt
        assertEquals(25300.0, im.beregnetInntekt, "beregnetInntekt")
        assertEquals(ÅrsakBeregnetInntektEndringKodeliste.Tariffendring, im.beregnetInntektEndringÅrsak, "endringÅrsak")
        // FullLønnIArbeidsgiverPerioden
        assertEquals(3220.0, im.fullLønnIArbeidsgiverPerioden.utbetalt)
        assertEquals(true, im.fullLønnIArbeidsgiverPerioden.utbetalerFullLønn)
        assertEquals(BegrunnelseIngenEllerRedusertUtbetalingKode.BeskjedGittForSent, im.fullLønnIArbeidsgiverPerioden.begrunnelse, "begrunnelse")
        // Refusjon
        assertEquals(123123.0, im.refusjon.refusjonPrMnd)
        assertEquals(LocalDate.of(2022, 9, 6), im.refusjon.refusjonOpphører)
        // Naturalytelser
        assertEquals(listOf(Naturalytelse(NaturalytelseKode.Bil, LocalDate.of(2022, 8, 8), 123.0)), im.naturalytelser)
        // Diverse
        assertNotNull(im.tidspunkt)
        assertNotNull(im.identitetsnummerInnsender)
    }

    @Test
    fun skal_håndtere_valgfrie_felter() {
        val im = mapInntektsmeldingDokument(objectMapper.readTree(objectMapper.writeValueAsString(INNTEKTMELDING_REQUEST_OPTIONALS)), fulltNavn, arbeidsgiver)
        // Inntekt
        assertNull(im.beregnetInntektEndringÅrsak, "endringÅrsak")
        // FullLønnIArbeidsgiverPerioden
        assertEquals(true, im.fullLønnIArbeidsgiverPerioden.utbetalerFullLønn)
        assertNull(im.fullLønnIArbeidsgiverPerioden.begrunnelse, "begrunnelse")
        assertNull(im.fullLønnIArbeidsgiverPerioden.utbetalt, "ubetalt")
        // Refusjon
        assertNull(im.refusjon.refusjonPrMnd, "refusjonPrMnd")
        assertNull(im.refusjon.refusjonOpphører, "refusjonOpphører")
    }

    @Test
    fun skal_håndtere_valgfrie_felter_fnutt_fnutt() {
        val im = mapInntektsmeldingDokument(objectMapper.readTree(objectMapper.writeValueAsString(INNTEKTMELDING_REQUEST_FNUTT)), fulltNavn, arbeidsgiver)
        // Inntekt
        assertNull(im.beregnetInntektEndringÅrsak, "endringÅrsak")
        // FullLønnIArbeidsgiverPerioden
        assertEquals(true, im.fullLønnIArbeidsgiverPerioden.utbetalerFullLønn)
        assertNull(im.fullLønnIArbeidsgiverPerioden.begrunnelse, "begrunnelse")
        assertNull(im.fullLønnIArbeidsgiverPerioden.utbetalt, "ubetalt")
        // Refusjon
        assertNull(im.refusjon.refusjonPrMnd, "refusjonPrMnd")
        assertNull(im.refusjon.refusjonOpphører, "refusjonOpphører")
    }
}
