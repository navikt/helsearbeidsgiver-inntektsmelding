package no.nav.helsearbeidsgiver.felles.test.loeser

import io.kotest.assertions.fail
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.every
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.loeser.Løser
import no.nav.helsearbeidsgiver.felles.loeser.Løsning
import no.nav.helsearbeidsgiver.felles.loeser.toLøsningFailure
import no.nav.helsearbeidsgiver.felles.loeser.toLøsningSuccess
import no.nav.helsearbeidsgiver.felles.test.mock.mockObject
import java.util.UUID

abstract class LøserTest {
    val testRapid = TestRapid()

    fun withTestRapid(initLøser: () -> Løser): Løser =
        mockObject(RapidApplication) {
            every { RapidApplication.create(any()) } returns testRapid

            initLøser()
        }

    data class LøserAnswer<out T : Any>(
        val behovType: BehovType,
        val initiateId: UUID,
        val løsning: Løsning<T>
    ) {
        companion object {
            inline fun <reified T : Any> fromJson(json: String): LøserAnswer<T> =
                json.let(Json::parseToJsonElement)
                    .jsonObject
                    .let {
                        val behov = behov(it)
                        LøserAnswer(
                            behovType = behov,
                            initiateId = initiateId(it),
                            løsning = løsning(it, behov)
                        )
                    }

            @PublishedApi
            internal fun behov(json: JsonObject): BehovType =
                json[Key.BEHOV.str]
                    .shouldNotBeNull()
                    .jsonArray[0]
                    .jsonPrimitive
                    .content
                    .let(BehovType::valueOf)

            @PublishedApi
            internal fun initiateId(json: JsonObject): UUID =
                json[Key.INITIATE_ID.str]
                    .shouldNotBeNull()
                    .jsonPrimitive
                    .content
                    .let(UUID::fromString)

            @PublishedApi
            internal inline fun <reified T : Any> løsning(json: JsonObject, behovType: BehovType): Løsning<T> =
                json[Key.LØSNING.str]
                    .shouldNotBeNull()
                    .jsonObject[behovType.name]
                    .shouldNotBeNull()
                    .toLøsning()
        }
    }
}

@PublishedApi
internal inline fun <reified T : Any> JsonElement.toLøsning(): Løsning<T> =
    mapOf<String, (JsonElement) -> Løsning<T>>(
        Løsning.Success<*>::resultat.name to {
            it.decode<T>().toLøsningSuccess()
        },
        Løsning.Failure::feilmelding.name to {
            it.decode<String>().toLøsningFailure()
        }
    )
        .firstNotNullOfOrNull { (field, transform) ->
            jsonObject[field]?.let(transform)
        }
        ?: fail("Deserialisering fra JsonElement til Løsning feilet.")

@PublishedApi
internal inline fun <reified T : Any> JsonElement.decode(): T =
    Json.decodeFromJsonElement(this)
