package no.nav.helsearbeidsgiver.felles

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class TypeWrappersTest : FunSpec({

    context("Orgnr") {
        context("gyldig") {
            withData(
                listOf(
                    "161231654",
                    "908498460",
                    "135103210",
                    "684603132",
                    "167484979",
                    "796033020"
                )
            ) {
                shouldNotThrowAny {
                    Orgnr(it)
                }
            }
        }

        context("ugyldig") {
            withData(
                listOf(
                    "123", // for kort
                    "0123456789", // for langt
                    "12x456789", // med bokstav
                    "1234 6789" // med mellomrom
                )
            ) {
                shouldThrowExactly<IllegalArgumentException> {
                    Orgnr(it)
                }
            }

            test("tom streng") {
                shouldThrowExactly<IllegalArgumentException> {
                    Orgnr("")
                }
            }
        }

        test("toString gir wrappet verdi") {
            Orgnr("123456789").let {
                it.toString() shouldBe it.verdi
            }
        }
    }

    context("Fnr") {
        context("gyldig") {
            withData(
                listOf(
                    // Sjekker ikke kontrollsiffer
                    "01010012345",
                    "19092212345",
                    "29104512345",
                    "31129912345",
                    "25056712345",
                    "11085812345",
                    "41066612345", // D-nummer
                    "45066612345", // D-nummer
                    "50066612345", // D-nummer
                    "57066612345", // D-nummer
                    "60066612345", // D-nummer
                    "69066612345", // D-nummer
                    "70066612345", // D-nummer
                    "71066612345", // D-nummer
                    "01490012345", // Testperson fra NAV
                    "01500012345", // Testperson fra NAV
                    "01890012345", // Testperson fra TestNorge
                    "01900012345" // Testperson fra TestNorge
                )
            ) {
                shouldNotThrowAny {
                    Fnr(it)
                }
            }
        }

        context("ugyldig") {
            withData(
                listOf(
                    "00010012345", // dag 0, andre siffer feil
                    "32010012345", // dag 32, andre siffer feil
                    "40010012345", // dag 40, andre siffer feil (D-nummer)
                    "72010012345", // dag 72, andre siffer feil (D-nummer)
                    "80010012345", // dag 80, første siffer feil
                    "01000012345", // måned 0, fjerde siffer feil
                    "01130012345", // måned 13, fjerde siffer feil
                    "01200012345", // måned 20, tredje siffer feil
                    "01390012345", // måned 39, tredje og fjerde siffer feil
                    "01400012345", // måned 40, fjerde siffer feil (testperson)
                    "01530012345", // måned 53, fjerde siffer feil (testperson)
                    "01790012345", // måned 79, tredje og fjerde siffer feil
                    "01800012345", // måned 80, fjerde siffer feil (testperson)
                    "01930012345", // måned 93, fjerde siffer feil (testperson)
                    "0101001234", // for kort
                    "010100123456", // for langt
                    "010100x2345", // med bokstav
                    "010100 1234" // med mellomrom
                )
            ) {
                shouldThrowExactly<IllegalArgumentException> {
                    Fnr(it)
                }
            }

            test("tom streng") {
                shouldThrowExactly<IllegalArgumentException> {
                    Fnr("")
                }
            }
        }

        test("toString gir wrappet verdi") {
            Fnr("24120612345").let {
                it.toString() shouldBe it.verdi
            }
        }
    }
})
