package no.nav.helsearbeidsgiver.inntektsmelding.selvbestemtlagreimservice

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.ArbeidsforholdType
import java.util.UUID

class SelvbestemtImUtilsTest :
    FunSpec({
        test("tilInntektsmeldingType mapper Skjema.ArbeidsforholdType til riktig Inntektsmelding.Type") {
            val id = UUID.randomUUID()

            ArbeidsforholdType.MedArbeidsforhold(UUID.randomUUID()).tilInntektsmeldingType(id) shouldBe Inntektsmelding.Type.Selvbestemt(id)
            ArbeidsforholdType.Fisker.tilInntektsmeldingType(id) shouldBe Inntektsmelding.Type.Fisker(id)
            ArbeidsforholdType.UtenArbeidsforhold.tilInntektsmeldingType(id) shouldBe Inntektsmelding.Type.UtenArbeidsforhold(id)
            ArbeidsforholdType.Behandlingsdager.tilInntektsmeldingType(id) shouldBe Inntektsmelding.Type.Behandlingsdager(id)
        }
    })
