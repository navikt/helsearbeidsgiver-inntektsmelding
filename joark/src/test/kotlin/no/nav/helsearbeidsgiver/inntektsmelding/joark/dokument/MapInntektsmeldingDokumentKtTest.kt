package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helsearbeidsgiver.inntektsmelding.joark.IM_VALID
import org.junit.jupiter.api.Assertions.assertEquals
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
        val im = mapInntektsmeldingDokument(objectMapper.readTree(objectMapper.writeValueAsString(IM_VALID)), fulltNavn, arbeidsgiver)
        assertEquals("123", im.identitetsnummer)
        assertEquals("abc", im.orgnrUnderenhet)
        assertEquals(listOf(LocalDate.of(2022, 10, 27), LocalDate.of(2022, 10, 26)), im.behandlingsdager)
        assertEquals(
            listOf(
                Periode(LocalDate.of(2022, 9, 1), LocalDate.of(2022, 9, 5)),
                Periode(LocalDate.of(2022, 9, 6), LocalDate.of(2022, 9, 15))
            ),
            im.egenmeldingsperioder
        )
        assertEquals(25300.0, im.beregnetInntekt)
        assertEquals(true, im.fullLønnIArbeidsgiverPerioden.utbetalerFullLønn)
        assertEquals("BeskjedGittForSent", im.fullLønnIArbeidsgiverPerioden.begrunnelse?.name)
        // assertEquals(true, im.refusjon.utbetalerHeleEllerDeler)
        assertEquals(123123.0, im.refusjon.refusjonPrMnd)
        assertEquals(LocalDate.of(2022, 9, 6), im.refusjon.refusjonOpphører)
        assertEquals(listOf(Naturalytelse(NaturalytelseKode.Bil, LocalDate.of(2022, 8, 8), 123.0)), im.naturalytelser)
    }
}
