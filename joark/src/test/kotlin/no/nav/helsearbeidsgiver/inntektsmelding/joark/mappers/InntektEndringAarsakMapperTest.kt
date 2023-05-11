package no.nav.helsearbeidsgiver.inntektsmelding.joark.mappers

import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Ferie
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Periode
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.Tariffendring
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.VarigLonnsendring
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mapstruct.factory.Mappers
import java.time.LocalDate

internal class InntektEndringAarsakMapperTest {

    @Test
    fun inntektEndringAarsakTilString() {
        val gjelderFra = LocalDate.of(2020, 1, 1)
        val gjelderTil = LocalDate.of(2020, 1, 3)
        val tariffendring = Tariffendring(gjelderFra, gjelderFra)
        val mapper = Mappers.getMapper(InntektEndringAarsakMapper::class.java)
        assertEquals("Tariffendring: fra $gjelderFra", mapper.inntektEndringAarsakTilString(tariffendring))

        val feriePerioder = listOf(Periode(gjelderFra, gjelderTil), Periode(gjelderFra, gjelderTil))
        val ferie = Ferie(feriePerioder)
        val ferieString = mapper.inntektEndringAarsakTilString(ferie)
        assertEquals("Ferie: fra $gjelderFra til $gjelderTil, fra $gjelderFra til $gjelderTil", ferieString)

        val varigLonnsendring = VarigLonnsendring(gjelderFra)
        assertEquals("Varig lønnsendring: fra $gjelderFra", mapper.inntektEndringAarsakTilString(varigLonnsendring))
    }
}
