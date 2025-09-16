package no.nav.helsearbeidsgiver.inntektsmelding.innsending.ekstern

import io.kotest.core.spec.style.FunSpec
import io.kotest.data.row
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntekt
import java.time.LocalDate
import java.time.YearMonth

class ValideringsUtilsTest :
    FunSpec({
        val testInntekt = Inntekt(beloep = 50000.0, inntektsdato = LocalDate.now(), naturalytelser = emptyList(), endringAarsaker = emptyList())

        context(Inntekt::validerInntektMotAordningen.name) {
            withData(
                mapOf(
                    "inntekt innenfor feilmargin gir OK validering" to row(
                        mapOf(
                            YearMonth.of(2024, 1) to 50005.0,
                            YearMonth.of(2024, 2) to 49995.0,
                            YearMonth.of(2024, 3) to 50000.0
                        ),
                        emptySet()
                    ),
                    "blandet null og ikke-null innenfor feilmargin gir OK validering" to row(
                        mapOf(
                            YearMonth.of(2024, 1) to 75000.0,
                            YearMonth.of(2024, 2) to null,
                            YearMonth.of(2024, 3) to 75000.0
                        ),
                        emptySet()
                    ),
                    "inntekt utenfor feilmargin gir valideringsfeil" to row(
                        mapOf(
                            YearMonth.of(2024, 1) to 49980.0,
                            YearMonth.of(2024, 2) to 49985.0,
                            YearMonth.of(2024, 3) to 49975.0
                        ),
                        setOf(Feilkode.INNTEKT_AVVIKER_FRA_A_ORDNINGEN)
                    ),
                    "alle a-ordning inntekter er null gir valideringsfeil" to row(
                        mapOf(
                            YearMonth.of(2024, 1) to null,
                            YearMonth.of(2024, 2) to null,
                            YearMonth.of(2024, 3) to null
                        ),
                        setOf(Feilkode.INNTEKT_AVVIKER_FRA_A_ORDNINGEN)
                    ),
                    "tom a-ordning map gir valideringsfeil" to row(
                        emptyMap<YearMonth, Double?>(),
                        setOf(Feilkode.INNTEKT_AVVIKER_FRA_A_ORDNINGEN)
                    )
                )
            ) { (aordningInntekt, forventetFeil) ->
                testInntekt.validerInntektMotAordningen(aordningInntekt) shouldBe forventetFeil
            }
        }
    })
