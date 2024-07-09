package no.nav.helsearbeidsgiver.inntektsmelding.api.validation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource

class TelefonnummerValidatorTest {
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["", " "])
    fun `Tomt eller null er ikke gyldig telefonnummer`(tlf: String?) {
        assertFalse(TelefonnummerValidator.isValid(tlf))
    }

    @Test
    fun `8 sifre er OK`() {
        val tlf = "12345678"
        assertTrue(TelefonnummerValidator.isValid(tlf))
    }

    @ParameterizedTest
    @ValueSource(strings = ["abcdefgh", "1234567a"])
    fun `Bokstaver er ikke gyldig`(tlf: String?) {
        assertFalse(TelefonnummerValidator.isValid(tlf))
    }

    @ParameterizedTest
    @ValueSource(strings = ["90000000", "+4790000000", "004790000000"])
    fun `Antall tegn må være 8, 11 eller 12`(tlf: String?) {
        assertTrue(TelefonnummerValidator.isValid(tlf))
    }

    @ParameterizedTest
    @ValueSource(strings = ["+90000000", "4790000000", "014790000000", "900000001", "0014790000000"])
    fun `ugyldige formater skal ikke godtas`(tlf: String?) {
        assertFalse(TelefonnummerValidator.isValid(tlf))
    }
}
