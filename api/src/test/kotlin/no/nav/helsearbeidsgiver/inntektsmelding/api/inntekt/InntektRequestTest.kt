package no.nav.helsearbeidsgiver.inntektsmelding.api.inntekt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.valiktor.constraints.NotBlank
import org.valiktor.test.shouldFailValidation
import java.time.LocalDate
import java.util.UUID

class InntektRequestTest : FunSpec({

    test("uuid skal ikke være tom") {

        shouldFailValidation<InntektRequest> {
            InntektRequest("", LocalDate.now()).validate()
        }.verify {
            expect(InntektRequest::uuid, "", NotBlank)
        }
    }

    test("slå sammen uuid og dato gir ok verdi for redis-key") {
        val uuid = UUID.randomUUID().toString()
        val dato = LocalDate.of(2020, 1, 1)
        val requestKey = InntektRequest(uuid, dato).requestKey()
        requestKey shouldBe uuid + "-" + "2020-01-01"
    }
})
