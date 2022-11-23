package no.nav.helsearbeidsgiver.inntektsmelding.joark

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

internal class MapInntektsmeldingKtTest {

    internal val objectMapper: ObjectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .registerModule(JavaTimeModule())

    @Test
    fun skal_kaste_exception_ved_feil() {
        val packet = mapOf(
            "identitetsnummer" to ""
        )
        assertThrows<UgyldigFormatException> {
            mapInntektsmelding(objectMapper.readTree(objectMapper.writeValueAsString(packet)))
        }
    }

    @Test
    fun skal_mappe_alle_data() {
        val im = mapInntektsmelding(objectMapper.readTree(objectMapper.writeValueAsString(IM_VALID)))
        assertEquals("123", im.identitetsnummer)
        assertEquals("abc", im.orgnrUnderenhet)
        assertEquals(listOf(LocalDate.of(2022, 10, 27), LocalDate.of(2022, 10, 26)), im.behandlingsdager)
        assertEquals(
            listOf(
                EgenmeldingPeriode(LocalDate.of(2022, 9, 1), LocalDate.of(2022, 9, 5)),
                EgenmeldingPeriode(LocalDate.of(2022, 9, 6), LocalDate.of(2022, 9, 15))
            ),
            im.egenmeldingsperioder
        )
        assertNotNull(im.bruttoInntekt)
        assertEquals(25300.0, im.bruttoInntekt.bruttoInntekt)
        assertEquals(true, im.bruttoInntekt.bekreftet)
        assertEquals(true, im.fullLønnIArbeidsgiverPerioden.utbetalerFullLønn)
        assertEquals("BeskjedGittForSent", im.fullLønnIArbeidsgiverPerioden.begrunnelse?.name)
        assertEquals(true, im.heleEllerdeler.utbetalerHeleEllerDeler)
        assertEquals(123123.0, im.heleEllerdeler.refusjonPrMnd)
        assertEquals(LocalDate.of(2022, 9, 6), im.heleEllerdeler.opphørSisteDag)
        assertEquals(listOf(Naturalytelse("abc", LocalDate.of(2022, 8, 8), 123.0)), im.naturalytelser)
        assertEquals(true, im.bekreftOpplysninger)
    }
}
