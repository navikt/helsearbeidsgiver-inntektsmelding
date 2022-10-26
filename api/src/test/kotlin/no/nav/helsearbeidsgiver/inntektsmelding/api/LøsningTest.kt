package no.nav.helsearbeidsgiver.inntektsmelding.api

import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.loeser.Løsning
import no.nav.helsearbeidsgiver.felles.loeser.LøsningFailure
import no.nav.helsearbeidsgiver.felles.loeser.LøsningSuccess
import no.nav.helsearbeidsgiver.felles.loeser.SealedClassMismatchedInputException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail

class LøsningTest {
    private val objectMapper = customObjectMapper()

    @Test
    fun `LøsningSuksess kan serialiseres og deserialiseres`() {
        val resultat = "hurra!"

        val løsningSerialized = LøsningSuccess(resultat).let(objectMapper::writeValueAsString)

        assertEquals("""{"resultat":"$resultat"}""", løsningSerialized)

        val løsningDeserialized = Løsning.read<String>(objectMapper, løsningSerialized)

        when (løsningDeserialized) {
            is LøsningSuccess ->
                assertEquals(løsningDeserialized.resultat, resultat)
            is LøsningFailure ->
                fail("Forventet ${LøsningSuccess::class.simpleName}, men fikk ${LøsningFailure::class.simpleName}")
        }
    }

    @Test
    fun `LøsningFailure kan serialiseres og deserialiseres`() {
        val feilmelding = "ånei!"

        val løsningSerialized = LøsningFailure(feilmelding).let(objectMapper::writeValueAsString)

        assertEquals("""{"feilmelding":"$feilmelding"}""", løsningSerialized)

        val løsningDeserialized = Løsning.read<String>(objectMapper, løsningSerialized)

        when (løsningDeserialized) {
            is LøsningSuccess ->
                fail("Forventet ${LøsningFailure::class.simpleName}, men fikk ${LøsningSuccess::class.simpleName}")
            is LøsningFailure ->
                assertEquals(løsningDeserialized.feilmelding, feilmelding)
        }
    }

    @Test
    fun `Ulesbar json gir SealedClassMismatchedInputException`() {
        assertThrows<SealedClassMismatchedInputException> {
            Løsning.read<String>(objectMapper, """{"neiognei":"lykke til med å lese denne"}""")
        }
    }
}
