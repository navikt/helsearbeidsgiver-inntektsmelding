@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonNull
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class ForespoerselSvarLoeserTest : FunSpec({
    val testRapid = TestRapid()

    ForespoerselSvarLoeser(testRapid)

    beforeTest {
        testRapid.reset()
    }

    withData(
        mapOf(
            "Ved suksessfullt svar på behov så publiseres data på simba-rapid" to mockForespoerselSvarMedSuksess(),
            "Ved suksessfullt svar med fastsatt inntekt på behov så publiseres data på simba-rapid" to mockForespoerselSvarMedSuksessMedFastsattInntekt()
        )
    ) { expectedIncoming ->
        val expected = PublishedData.mock(expectedIncoming)

        testRapid.sendJson(
            Pri.Key.BEHOV to ForespoerselSvar.behovType.toJson(Pri.BehovType.serializer()),
            Pri.Key.LØSNING to expectedIncoming.toJson(ForespoerselSvar.serializer())
        )

        val actual = testRapid.firstMessage().fromJson(PublishedData.serializer())

        testRapid.inspektør.size shouldBeExactly 1
        actual shouldBe expected
    }

    test("Ved feil så publiseres feil på simba-rapid") {
        val expectedIncoming = mockForespoerselSvarMedFeil()

        val expected = PublishedFeil.mock(expectedIncoming)

        testRapid.sendJson(
            Pri.Key.BEHOV to ForespoerselSvar.behovType.toJson(Pri.BehovType.serializer()),
            Pri.Key.LØSNING to expectedIncoming.toJson(ForespoerselSvar.serializer())
        )

        val actual = testRapid.firstMessage().fromJson(PublishedFeil.serializer())

        testRapid.inspektør.size shouldBeExactly 1
        actual shouldBe expected
    }
})

@Serializable
@OptIn(ExperimentalSerializationApi::class)
private data class PublishedData(
    @JsonNames("@event_name")
    val eventName: EventName,
    val uuid: UUID,
    @JsonNames("forespoersel-svar")
    val forespoerselSvar: TrengerInntekt
) {
    @EncodeDefault
    val data = ""

    companion object {
        fun mock(forespoerselSvar: ForespoerselSvar): PublishedData {
            val boomerangMap = forespoerselSvar.boomerang.toMap()

            val initiateEvent = Key.EVENT_NAME.les(EventName.serializer(), boomerangMap)
            val transaksjonId = Key.UUID.les(UuidSerializer, boomerangMap)

            return PublishedData(
                eventName = initiateEvent,
                uuid = transaksjonId,
                forespoerselSvar = forespoerselSvar.resultat?.toTrengerInntekt().shouldNotBeNull()
            )
        }
    }
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
private data class PublishedFeil(
    @JsonNames("@event_name")
    val eventName: EventName,
    val fail: Map<String, JsonElement>,
    val uuid: UUID
) {
    companion object {
        fun mock(forespoerselSvar: ForespoerselSvar): PublishedFeil {
            val feilmelding = "Klarte ikke hente forespørsel. Feilet med kode '${forespoerselSvar.feil?.name.shouldNotBeNull()}'."

            val boomerangMap = forespoerselSvar.boomerang.toMap()

            val initiateEvent = Key.EVENT_NAME.les(EventName.serializer(), boomerangMap)
            val transaksjonId = Key.UUID.les(UuidSerializer, boomerangMap)

            return PublishedFeil(
                eventName = initiateEvent,
                fail = mapOf(
                    Fail::behov.name to BehovType.HENT_TRENGER_IM.toJson(),
                    Fail::feilmelding.name to feilmelding.toJson(),
                    Fail::data.name to JsonNull,
                    Fail::uuid.name to transaksjonId.toJson(),
                    Fail::forespørselId.name to forespoerselSvar.forespoerselId.toJson()
                ),
                uuid = transaksjonId
            )
        }
    }
}
