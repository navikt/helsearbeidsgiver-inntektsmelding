package no.nav.hag.simba.utils.felles.utils

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import no.nav.hag.simba.utils.felles.test.mock.mockAvsenderSystem
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import java.util.UUID

class InntektsmeldingUtilsKtTest :
    FunSpec({
        context(Inntektsmelding.Type::erForespurt.name) {
            withData(
                nameFn = { it.first::class.simpleName!! },
                Inntektsmelding.Type.Forespurt(UUID.randomUUID()) to true,
                Inntektsmelding.Type.ForespurtEkstern(UUID.randomUUID(), true, mockAvsenderSystem()) to true,
                Inntektsmelding.Type.Selvbestemt(UUID.randomUUID()) to false,
                Inntektsmelding.Type.Fisker(UUID.randomUUID()) to false,
                Inntektsmelding.Type.UtenArbeidsforhold(UUID.randomUUID()) to false,
                Inntektsmelding.Type.Behandlingsdager(UUID.randomUUID()) to false,
            ) { (type, expected) ->
                type.erForespurt() shouldBe expected
            }
        }
    })
