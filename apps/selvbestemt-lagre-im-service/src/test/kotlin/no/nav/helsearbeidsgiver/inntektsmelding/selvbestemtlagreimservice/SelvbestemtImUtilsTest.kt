package no.nav.helsearbeidsgiver.inntektsmelding.selvbestemtlagreimservice

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import no.nav.hag.simba.utils.felles.test.mock.mockFlereArbeidsforhold
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.ArbeidsforholdType
import java.util.UUID

class SelvbestemtImUtilsTest :
    FunSpec({
        test("ArbeidsforholdType.MedArbeidsforhold mappes til korrekt Inntektsmelding.Type") {
            val id = UUID.randomUUID()
            val flereArbeidsforhold = mockFlereArbeidsforhold()
            val medArbeidsforhold = ArbeidsforholdType.MedArbeidsforhold(UUID.randomUUID())

            medArbeidsforhold.tilInntektsmeldingType(id, null) shouldBe Inntektsmelding.Type.Selvbestemt(id, null)
            medArbeidsforhold.tilInntektsmeldingType(id, flereArbeidsforhold) shouldBe Inntektsmelding.Type.Selvbestemt(id, flereArbeidsforhold)
        }

        context("ArbeidsforholdType mappes til korrekt Inntektsmelding.Type") {
            withData(
                nameFn = { it.first::class.simpleName.orEmpty() },
                listOf<Pair<ArbeidsforholdType, (UUID) -> Inntektsmelding.Type>>(
                    ArbeidsforholdType.Behandlingsdager to { Inntektsmelding.Type.Behandlingsdager(it) },
                    ArbeidsforholdType.Fisker to { Inntektsmelding.Type.Fisker(it) },
                    ArbeidsforholdType.UtenArbeidsforhold to { Inntektsmelding.Type.UtenArbeidsforhold(it) },
                ),
            ) { (arbeidsforholdType, imTypeMedId) ->
                val id = UUID.randomUUID()
                arbeidsforholdType.tilInntektsmeldingType(id, null) shouldBe imTypeMedId(id)

                // Flere arbeidsforhold skal ignoreres for arbeidsforholdstyper utenom 'MedArbeidsforhold'
                arbeidsforholdType.tilInntektsmeldingType(id, mockFlereArbeidsforhold()) shouldBe imTypeMedId(id)
            }
        }
    })
