package no.nav.helsearbeidsgiver.inntektsmelding.joark

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
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
        val packet = mapOf(
            "identitetsnummer" to "123",
            "orgnrUnderenhet" to "abc",
            "behandlingsdagerFom" to "2022-10-01",
            "behandlingsdagerTom" to "2022-10-11",
            "behandlingsdager" to listOf("2022-10-27", "2022-10-26"),
            "egenmeldinger" to listOf(
                mapOf("fom" to "2022-09-01", "tom" to "2022-09-05"),
                mapOf("fom" to "2022-09-06", "tom" to "2022-09-15")
            ),
            "bruttonInntekt" to "25300",
            "bruttoBekreftet" to "true",
            "utbetalerFull" to "true",
            "begrunnelseRedusert" to "BeskjedGittForSent",
            "utbetalerHeleEllerDeler" to "true",
            "refusjonPrMnd" to "19500",
            "opphørerKravet" to "true",
            "opphørSisteDag" to "2022-10-11",
            "naturalytelser" to listOf(
                mapOf(
                    "naturalytelseKode" to "abc",
                    "dato" to "2022-08-08",
                    "beløp" to "123"
                )
            ),
            "bekreftOpplysninger" to "true"
        )
        val im = mapInntektsmelding(objectMapper.readTree(objectMapper.writeValueAsString(packet)))
        assertEquals("123", im.identitetsnummer)
        assertEquals("abc", im.orgnrUnderenhet)
        assertEquals(LocalDate.of(2022, 10, 1), im.behandlingsdagerFom)
        assertEquals(LocalDate.of(2022, 10, 11), im.behandlingsdagerTom)
        assertEquals(listOf(LocalDate.of(2022, 10, 27), LocalDate.of(2022, 10, 26)), im.behandlingsdager)
        assertEquals(
            listOf(
                Egenmelding(LocalDate.of(2022, 9, 1), LocalDate.of(2022, 9, 5)),
                Egenmelding(LocalDate.of(2022, 9, 6), LocalDate.of(2022, 9, 15))
            ),
            im.egenmeldinger
        )
        assertEquals(25300.0, im.bruttonInntekt)
        assertEquals(true, im.bruttoBekreftet)
        assertEquals(true, im.utbetalerFull)
        assertEquals("BeskjedGittForSent", im.begrunnelseRedusert)
        assertEquals(true, im.utbetalerHeleEllerDeler)
        assertEquals(19500.0, im.refusjonPrMnd)
        assertEquals(true, im.opphørerKravet)
        assertEquals(LocalDate.of(2022, 10, 11), im.opphørSisteDag)
        assertEquals(listOf(Naturalytelse("abc", LocalDate.of(2022, 8, 8), 123.0)), im.naturalytelser)
        assertEquals(true, im.bekreftOpplysninger)
    }
}
