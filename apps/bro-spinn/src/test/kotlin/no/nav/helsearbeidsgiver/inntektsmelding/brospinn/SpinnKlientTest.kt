package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import no.nav.helsearbeidsgiver.utils.test.resource.readResource
import no.nav.inntektsmeldingkontrakt.AvsenderSystem
import java.util.UUID

class SpinnKlientTest :
    FunSpec({

        val expectedJson = "gyldigRespons.json".readResource()
        val expectedInntektsmelding = Jackson.fromJson(expectedJson)

        test("Hvis inntektsmelding finnes returneres system navn") {
            val spinnKlient = mockSpinnKlient(HttpStatusCode.OK to expectedJson)
            val result = spinnKlient.hentEksternInntektsmelding(UUID.randomUUID())
            result.avsenderSystemNavn shouldBe "NAV_NO"
        }

        test("Hvis inntektsmelding finnes men mangler avsenderSystemNavn kastes feil") {
            val responsData = Jackson.toJson(expectedInntektsmelding.copy(avsenderSystem = AvsenderSystem(null, null)))
            val spinnKlient = mockSpinnKlient(HttpStatusCode.OK to responsData)

            val exception =
                shouldThrowExactly<SpinnApiException> {
                    spinnKlient.hentEksternInntektsmelding(UUID.randomUUID())
                }
            exception.message shouldBe MANGLER_AVSENDER
        }

        test("feiler ved 4xx-feil") {
            val spinnKlient = mockSpinnKlient(HttpStatusCode.NotFound to "")
            val exception =
                shouldThrowExactly<SpinnApiException> {
                    spinnKlient.hentEksternInntektsmelding(UUID.randomUUID())
                }
            exception.message shouldBe "$FIKK_SVAR_MED_RESPONSE_STATUS: ${HttpStatusCode.NotFound.value}"
        }

        test("lykkes ved færre 5xx-feil enn max retries (5)") {
            val spinnKlient =
                mockSpinnKlient(
                    HttpStatusCode.InternalServerError to "",
                    HttpStatusCode.InternalServerError to "",
                    HttpStatusCode.InternalServerError to "",
                    HttpStatusCode.InternalServerError to "",
                    HttpStatusCode.InternalServerError to "",
                    HttpStatusCode.OK to expectedJson,
                )

            shouldNotThrowAny {
                spinnKlient.hentEksternInntektsmelding(UUID.randomUUID())
            }
        }

        test("feiler ved flere 5xx-feil enn max retries (5)") {
            val spinnKlient =
                mockSpinnKlient(
                    HttpStatusCode.InternalServerError to "",
                    HttpStatusCode.InternalServerError to "",
                    HttpStatusCode.InternalServerError to "",
                    HttpStatusCode.InternalServerError to "",
                    HttpStatusCode.InternalServerError to "",
                    HttpStatusCode.InternalServerError to "",
                )

            shouldThrowExactly<ServerResponseException> {
                spinnKlient.hentEksternInntektsmelding(UUID.randomUUID())
            }
        }

        test("kall feiler og prøver på nytt ved timeout") {
            val spinnKlient =
                mockSpinnKlient(
                    HttpStatusCode.OK to "timeout",
                    HttpStatusCode.OK to "timeout",
                    HttpStatusCode.OK to "timeout",
                    HttpStatusCode.OK to "timeout",
                    HttpStatusCode.OK to "timeout",
                    HttpStatusCode.OK to expectedJson,
                )

            shouldNotThrowAny {
                spinnKlient.hentEksternInntektsmelding(UUID.randomUUID())
            }
        }
    })
