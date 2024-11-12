@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.test.json.lesFail
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class ForespoerselSvarRiverTest :
    FunSpec({
        val testRapid = TestRapid()

        ForespoerselSvarRiver().connect(testRapid)

        beforeTest {
            testRapid.reset()
        }

        withData(
            mapOf(
                "Ved suksessfullt svar på behov så publiseres data på simba-rapid" to mockForespoerselSvarMedSuksess(),
                "Ved suksessfullt svar med fastsatt inntekt på behov så publiseres data på simba-rapid" to mockForespoerselSvarMedSuksessMedFastsattInntekt(),
            ),
        ) { expectedIncoming ->
            val expected = PublishedData.mock(expectedIncoming)

            testRapid.sendJson(
                Pri.Key.BEHOV to Pri.BehovType.TRENGER_FORESPØRSEL.toJson(Pri.BehovType.serializer()),
                Pri.Key.LØSNING to expectedIncoming.toJson(ForespoerselSvar.serializer()),
            )

            val actual = testRapid.firstMessage().fromJson(PublishedData.serializer())

            testRapid.inspektør.size shouldBeExactly 1

            actual shouldBe expected
        }

        test("Ved feil så publiseres feil på simba-rapid") {
            val expectedIncoming = mockForespoerselSvarMedFeil()

            val expected = mockFail(expectedIncoming)

            testRapid.sendJson(
                Pri.Key.BEHOV to Pri.BehovType.TRENGER_FORESPØRSEL.toJson(Pri.BehovType.serializer()),
                Pri.Key.LØSNING to expectedIncoming.toJson(ForespoerselSvar.serializer()),
            )

            val actual = testRapid.firstMessage().lesFail()

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
    val data: Map<Key, JsonElement>,
) {
    companion object {
        fun mock(forespoerselSvar: ForespoerselSvar): PublishedData {
            val boomerangMap = forespoerselSvar.boomerang.toMap()

            val eventName = Key.EVENT_NAME.les(EventName.serializer(), boomerangMap)
            val transaksjonId = Key.UUID.les(UuidSerializer, boomerangMap)
            val data = boomerangMap[Key.DATA]?.toMap().orEmpty()

            return PublishedData(
                eventName = eventName,
                uuid = transaksjonId,
                data =
                    data.plus(
                        mapOf(
                            Key.FORESPOERSEL_ID to forespoerselSvar.forespoerselId.toJson(),
                            Key.FORESPOERSEL_SVAR to
                                forespoerselSvar.resultat
                                    .shouldNotBeNull()
                                    .toForespoersel()
                                    .toJson(Forespoersel.serializer()),
                            Key.FORESPOERSEL_SVAR_V2 to
                                forespoerselSvar.resultat
                                    .shouldNotBeNull()
                                    .toForespoersel()
                                    .toJson(Forespoersel.serializer()),
                        ),
                    ),
            )
        }
    }
}

fun mockFail(forespoerselSvar: ForespoerselSvar): Fail {
    val feilmelding = "Klarte ikke hente forespørsel. Feilet med kode '${forespoerselSvar.feil?.name.shouldNotBeNull()}'."

    val boomerangMap = forespoerselSvar.boomerang.toMap()

    val eventName = Key.EVENT_NAME.les(EventName.serializer(), boomerangMap)
    val transaksjonId = Key.UUID.les(UuidSerializer, boomerangMap)
    val data = boomerangMap[Key.DATA]?.toMap().orEmpty()

    return Fail(
        feilmelding = feilmelding,
        event = eventName,
        transaksjonId = transaksjonId,
        forespoerselId = forespoerselSvar.forespoerselId,
        utloesendeMelding =
            mapOf(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.DATA to data.toJson(),
            ).toJson(),
    )
}
