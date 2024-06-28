package no.nav.helsearbeidsgiver.felles

import io.kotest.core.spec.style.FunSpec
import io.kotest.data.row
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr

class PersonTest : FunSpec({
    withData(
        nameFn = { (fnr, _) -> fnr },
        listOf(
            row("01015030069", 1.januar(1950)),
            row("01025090035", 1.februar(1950)),
            row("01033030092", 1.mars(1930)),
            row("01041070087", 1.april(2010))
        )
    ) { (fnr, forventetDato) ->
        Person.foedselsdato(Fnr(fnr)) shouldBe forventetDato
    }
})
