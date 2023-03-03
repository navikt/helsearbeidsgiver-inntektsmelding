package no.nav.helsearbeidsgiver.felles

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.felles.test.date.januar

class PeriodeTest : FunSpec({

    test("er sammenhengende periode") {
        val p1 = 1.januar til 2.januar
        val p2 = 3.januar til 5.januar
        p1.erSammenhengende(p2).shouldBeTrue()
        p2.erSammenhengende(p1).shouldBeTrue()
    }

    test("en dags opphold eller flere er ikke sammenhengende") {
        val p1 = 1.januar til 2.januar
        val p2 = 4.januar til 5.januar
        p1.erSammenhengende(p2).shouldBeFalse()
        p2.erSammenhengende(p1).shouldBeFalse()
    }

    test("slå sammen to sammenhengende perioder") {
        val p1 = 1.januar til 2.januar
        val p2 = 3.januar til 5.januar
        p1.slåSammenOrNull(p2) shouldBe (1.januar til 5.januar)
    }

    test("ikke slå sammen to ikke-sammenhengende perioder") {
        val p1 = 1.januar til 2.januar
        val p2 = 4.januar til 5.januar
        p1.slåSammenOrNull(p2).shouldBeNull()
    }
})
