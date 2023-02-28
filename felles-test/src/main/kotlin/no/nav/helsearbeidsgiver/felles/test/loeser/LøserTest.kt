package no.nav.helsearbeidsgiver.felles.test.loeser

import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.every
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.list
import no.nav.helsearbeidsgiver.felles.json.løsning
import no.nav.helsearbeidsgiver.felles.loeser.Løser
import no.nav.helsearbeidsgiver.felles.loeser.Løsning
import no.nav.helsearbeidsgiver.felles.serializers.UuidSerializer
import no.nav.helsearbeidsgiver.felles.test.mock.mockObject
import java.util.UUID

abstract class LøserTest {
    val testRapid = TestRapid()

    fun <T : Any> withTestRapid(initLøser: () -> Løser<T>): Løser<T> =
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
            fun <T : Any> JsonElement.toLøserAnswer(tSerializer: KSerializer<T>): LøserAnswer<T> =
                jsonObject
                    .let {
                        val behov = behov(it)
                        LøserAnswer(
                            behovType = behov,
                            initiateId = initiateId(it),
                            løsning = løsning(it, behov, tSerializer)
                        )
                    }

            private fun behov(json: JsonObject): BehovType =
                json.get(Key.BEHOV)
                    .fromJson(BehovType.serializer().list())
                    .first()

            private fun initiateId(json: JsonObject): UUID =
                json.get(Key.INITIATE_ID)
                    .fromJson(UuidSerializer)

            private fun <T : Any> løsning(json: JsonObject, behovType: BehovType, tSerializer: KSerializer<T>): Løsning<T> =
                json.get(Key.LØSNING)
                    .fromJson(
                        MapSerializer(
                            BehovType.serializer(),
                            JsonElement.serializer()
                        )
                    )
                    .get(behovType)
                    .shouldNotBeNull()
                    .fromJson(tSerializer.løsning())
        }
    }
}

private fun JsonObject.get(key: Key): JsonElement =
    get(key.str).shouldNotBeNull()
