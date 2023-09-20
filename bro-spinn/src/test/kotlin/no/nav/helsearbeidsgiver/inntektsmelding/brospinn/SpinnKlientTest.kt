package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import no.nav.helsearbeidsgiver.felles.json.Jackson
import no.nav.helsearbeidsgiver.utils.test.resource.readResource
import no.nav.inntektsmeldingkontrakt.AvsenderSystem
import no.nav.inntektsmeldingkontrakt.Inntektsmelding

class SpinnKlientTest : FunSpec({

    val expectedJson = "gyldigRespons.json".readResource()
    val expectedInntektsmelding = Jackson.fromJson<Inntektsmelding>(expectedJson)

    test("Hvis inntektsmelding ikke finnes kastes feil") {
        val spinnKlient = mockSpinnKlient("", HttpStatusCode.NotFound)
        val exception = shouldThrowExactly<SpinnApiException> {
            spinnKlient.hentEksternInntektsmelding("abc-1")
        }
        exception.message shouldBe "$FIKK_SVAR_MED_RESPONSE_STATUS: ${HttpStatusCode.NotFound.value}"
    }

    test("Hvis inntektsmelding finnes men mangler avsenderSystemNavn kastes feil") {
        val responsData = Jackson.toJson(expectedInntektsmelding.copy(avsenderSystem = AvsenderSystem(null, null)))
        val spinnKlient = mockSpinnKlient(responsData, HttpStatusCode.OK)

        val exception = shouldThrowExactly<SpinnApiException> {
            spinnKlient.hentEksternInntektsmelding("abc-1")
        }
        exception.message shouldBe MANGLER_AVSENDER
    }
    test("Hvis inntektsmelding finnes returneres system navn") {
        val spinnKlient = mockSpinnKlient(expectedJson, HttpStatusCode.OK)
        val result = spinnKlient.hentEksternInntektsmelding("abc-1")
        result.avsenderSystemNavn shouldBe "NAV_NO"
    }
})
