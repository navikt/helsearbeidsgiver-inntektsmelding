package no.nav.helsearbeidsgiver.inntektsmelding.joark.mappers

import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Bonus
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Feilregistrert
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Ferie
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Tariffendring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.VarigLonnsendring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mapstruct.factory.Mappers
import java.time.LocalDate

class InntektEndringAarsakMapperTest {
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

        val bonus = Bonus()
        assertEquals("Bonus", mapper.inntektEndringAarsakTilString(bonus))

        val feilregistrert = Feilregistrert
        assertEquals("Mangelfull eller uriktig rapportering til A-ordningen", mapper.inntektEndringAarsakTilString(feilregistrert))
    }
}
