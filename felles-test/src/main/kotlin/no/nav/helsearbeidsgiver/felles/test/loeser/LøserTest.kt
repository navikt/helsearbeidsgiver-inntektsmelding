package no.nav.helsearbeidsgiver.felles.test.loeser

import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.every
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.parseJson
import no.nav.helsearbeidsgiver.felles.loeser.Løser
import no.nav.helsearbeidsgiver.felles.loeser.Løsning
import no.nav.helsearbeidsgiver.felles.loeser.Løsning.Companion.toLøsning
import no.nav.helsearbeidsgiver.felles.serializers.UuidSerializer
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
                json.parseJson()
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
                json.get(Key.BEHOV)
                    .fromJson<List<BehovType>>()
                    .first()

            @PublishedApi
            internal fun initiateId(json: JsonObject): UUID =
                json.get(Key.INITIATE_ID)
                    .fromJson(UuidSerializer)

            @PublishedApi
            internal inline fun <reified T : Any> løsning(json: JsonObject, behovType: BehovType): Løsning<T> =
                json.get(Key.LØSNING)
                    .fromJson<Map<BehovType, JsonElement>>()
                    .get(behovType)
                    .shouldNotBeNull()
                    .toLøsning()
        }
    }
}

@PublishedApi
internal fun JsonObject.get(key: Key): JsonElement =
    get(key.str).shouldNotBeNull()
