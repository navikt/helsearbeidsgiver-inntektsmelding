package no.nav.helsearbeidsgiver.inntektsmelding.api.validation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class IsValidBehandlingsdagerKtTest {

    val now = LocalDate.now()

    @Test
    fun `skal godta uten dato`() {
        assertTrue(isValidBehandlingsdager(emptyList()))
    }

    @Test
    fun `skal godta kun en dato`() {
        assertTrue(isValidBehandlingsdager(listOf(now)))
    }

    @Test
    fun `skal ikke godta flere enn 12 dager`() {
        assertFalse(
            isValidBehandlingsdager(
                listOf(
                    now,
                    now.plusDays(1), now.plusDays(2), now.plusDays(3),
                    now.plusDays(4), now.plusDays(5), now.plusDays(6),
                    now.plusDays(7), now.plusDays(8), now.plusDays(9),
                    now.plusDays(10), now.plusDays(11), now.plusDays(12)
                )
            )
        )
    }

    @Test
    @Disabled
    fun `skal ikke godta flere samme uke`() {
        assertFalse(isValidBehandlingsdager(listOf(now, now.plusDays(1))))
    }

    @Test
    @Disabled
    fun `skal ikke godta opphold på mer enn 15 dager`() {
        assertFalse(isValidBehandlingsdager(listOf(now, now.plusDays(16))))
    }
}
