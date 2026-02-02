package no.nav.helsearbeidsgiver.inntektsmelding.joark.dokument

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class TekstUtilsKtTest :
    FunSpec({

        context(Double::tilNorskFormat.name) {
            withData(
                nameFn = { (tall, forventetTekst) -> "'$tall' formateres til '$forventetTekst'" },
                0.0 to "0,00",
                0.7 to "0,70",
                1.0 to "1,00",
                1.3 to "1,30",
                1.34 to "1,34",
                1.344 to "1,34",
                1.345 to "1,35",
                1.3444 to "1,34",
                1.3445 to "1,34",
                2000.0 to "2 000,00",
                5444.0 to "5 444,00",
                55444.0 to "55 444,00",
                555444.0 to "555 444,00",
                7766700.77 to "7 766 700,77",
                67766700.77 to "67 766 700,77",
                667766700.77 to "667 766 700,77",
                3888333888.33 to "3 888 333 888,33",
            ) { (tall, forventetTekst) ->
                tall.tilNorskFormat() shouldBe forventetTekst
            }
        }
    })
