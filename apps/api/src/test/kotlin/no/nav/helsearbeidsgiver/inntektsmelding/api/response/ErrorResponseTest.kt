package no.nav.helsearbeidsgiver.inntektsmelding.api.response

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainAll
import io.kotest.matchers.string.shouldStartWith
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class ErrorResponseTest :
    FunSpec({

        test("alle subklasser testes") {
            errors.size shouldBeExactly ErrorResponse::class.sealedSubclasses.size
        }

        errors.forEach { error ->
            context("${error::class.simpleName}") {

                test("'errorId' er nedkortet versjon av 'kontekstId'") {
                    error.kontekstId.toString().shouldStartWith(error.errorId)
                }

                test("json inneholder grunnleggende felt") {
                    val expectedBaselineJson =
                        mapOf(
                            error::kontekstId.name to error.kontekstId.toJson(),
                            error::error.name to error.error.toJson(),
                            error::errorId.name to error.errorId.toJson(),
                        )

                    val actualJson =
                        error
                            .toJson(ErrorResponse.serializer())
                            .fromJsonMapFiltered(String.serializer())

                    actualJson shouldContainAll expectedBaselineJson
                }
            }
        }
    })

private val errors =
    listOf(
        ErrorResponse.Unknown(
            kontekstId = UUID.randomUUID(),
        ),
        ErrorResponse.JsonSerialization(
            kontekstId = UUID.randomUUID(),
            forespoerselId = UUID.randomUUID().toString(),
            inntektsmeldingTypeId = UUID.randomUUID(),
        ),
        ErrorResponse.Validering(
            kontekstId = UUID.randomUUID(),
            valideringsfeil = setOf("livet er for kjipt", "det regner hele året"),
        ),
        ErrorResponse.RedisTimeout(
            kontekstId = UUID.randomUUID(),
            uuid = UUID.randomUUID(),
            inntektsmeldingTypeId = UUID.randomUUID(),
        ),
        ErrorResponse.Arbeidsforhold(
            kontekstId = UUID.randomUUID(),
            inntektsmeldingTypeId = UUID.randomUUID(),
        ),
    )
