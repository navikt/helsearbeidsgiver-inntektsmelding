package no.nav.helsearbeidsgiver.inntektsmelding.innsending.ekstern

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntekt
import java.time.LocalDate
import java.time.YearMonth

class ValideringsUtilsTest :
    FunSpec({
        val testInntekt =
            Inntekt(
                beloep = 50000.0,
                inntektsdato = LocalDate.now(),
                endringAarsaker = emptyList(),
            )
        val testAar = 2024

        val feil = Feil(Feilkode.INNTEKT_AVVIKER_FRA_A_ORDNINGEN, "whatever")

        context(Inntekt::validerInntektMotAordningen.name) {
            withData(
                mapOf(
                    "inntekt innenfor feilmargin gir OK validering" to
                        Pair(
                            listOf(
                                50005.0,
                                49995.0,
                                50000.0,
                            ),
                            emptySet(),
                        ),
                    "blandet null og ikke-null innenfor feilmargin gir OK validering" to
                        Pair(
                            listOf(
                                75000.0,
                                null,
                                75000.0,
                            ),
                            emptySet(),
                        ),
                    "inntekt utenfor feilmargin gir valideringsfeil" to
                        Pair(
                            listOf(
                                49980.0,
                                49985.0,
                                49975.0,
                            ),
                            setOf(
                                feil,
                            ),
                        ),
                    "alle a-ordninginntekter er null gir valideringsfeil" to
                        Pair(
                            listOf(
                                null,
                                null,
                                null,
                            ),
                            setOf(
                                feil,
                            ),
                        ),
                    "tom a-ordning map gir valideringsfeil" to
                        Pair(
                            emptyList(),
                            setOf(
                                feil,
                            ),
                        ),
                ),
            ) { (aordningInntektListe, forventetFeil) ->
                val aordningInntektMap =
                    aordningInntektListe
                        .withIndex()
                        .associate { YearMonth.of(testAar, it.index + 1) to it.value }
                if (forventetFeil.isEmpty()) {
                    testInntekt.validerInntektMotAordningen(aordningInntektMap) shouldBe forventetFeil
                } else {
                    testInntekt.validerInntektMotAordningen(aordningInntektMap).first().feilkode shouldBe forventetFeil.first().feilkode
                }
            }
        }
    })
