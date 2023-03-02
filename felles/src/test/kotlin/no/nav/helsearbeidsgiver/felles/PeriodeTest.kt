package no.nav.helsearbeidsgiver.felles

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import no.nav.helsearbeidsgiver.felles.test.date.februar
import no.nav.helsearbeidsgiver.felles.test.date.januar

class PeriodeTest : FunSpec({

    test("to like perioder overlapper") {
        val p1 = Periode(1.januar, 31.januar)
        val p2 = p1.copy()
        p2.overlapper(p1).shouldBeTrue()
        p1.overlapper(p2).shouldBeTrue()
    }

    test("to ikke-overlappende perioder overlapper ikke") {
        val p1 = Periode(1.januar, 31.januar)
        val p2 = Periode(5.februar(2023), 10.februar(2023))
        p2.overlapper(p1).shouldBeFalse()
        p1.overlapper(p2).shouldBeFalse()
    }

    test("fullstendig overlappende overlapper") {
        val p1 = Periode(1.januar, 31.januar)
        val p2 = Periode(2.januar, 5.januar)
        p2.overlapper(p1).shouldBeTrue()
        p1.overlapper(p2).shouldBeTrue()
    }

    test("delvis overlappende overlapper") {
        val p1 = Periode(1.januar, 31.januar)
        val p2 = Periode(30.januar, 5.februar)
        p2.overlapper(p1).shouldBeTrue()
        p1.overlapper(p2).shouldBeTrue()
    }

    test("ny periode starter på samme dag som forrige periode slutter skal overlappe") {
        val p1 = Periode(1.januar, 2.januar)
        val p2 = Periode(2.januar, 3.januar)
        p1.overlapper(p2).shouldBeTrue()
        p2.overlapper(p1).shouldBeTrue()
    }

    test("start på samme dag overlapper") {
        val p1 = Periode(1.januar, 2.januar)
        val p2 = Periode(1.januar, 3.januar)
        p1.overlapper(p2).shouldBeTrue()
        p2.overlapper(p1).shouldBeTrue()
    }
})
