package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import kotlinx.serialization.SerializationException

class ImTestRapidTest :
    FunSpec({

        val imTestRapid = ImTestRapid()

        context("publisering av gyldig json som ikke er objekt så kastes exception") {
            withData(
                "null",
                "false",
                "true",
                "13.37",
                "\"hola señor\"",
                "[1,2,3]",
                "[1,true,\"tre\"]",
            ) { json ->
                shouldThrowExactly<JsonObjectRequired> {
                    imTestRapid.publish(json)
                }
            }
        }

        context("publisering av ugyldig json kaster exception") {
            withData(
                "13,37",
                "streng uten fnutter",
                "[\"tall\":666]",
                "{key:value}",
                "{1:1,2:2}",
                "{\"key\"=\"value\"}",
                "\"key\"=\"value\"",
            ) { json ->
                shouldThrow<SerializationException> {
                    imTestRapid.publish(json)
                }
            }
        }
    })
