@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.test.json.readFail
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselListeSvar
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class VedtaksperiodeIdForespoerselSvarRiverTest :
    FunSpec({
        val testRapid = TestRapid()

        VedtaksperiodeIdForespoerselSvarRiver().connect(testRapid)

        beforeTest {
            testRapid.reset()
        }

        test("Ved suksessfullt svar på behov så publiseres data på simba-rapid") {
            val expectedIncoming = mockForespoerselListeSvarMedSuksess()
            val expected = PublishedData.mock(expectedIncoming)

            testRapid.sendJson(
                Pri.Key.BEHOV to Pri.BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE.toJson(Pri.BehovType.serializer()),
                Pri.Key.LØSNING to expectedIncoming.toJson(ForespoerselListeSvar.serializer()),
            )

            val actual = testRapid.firstMessage().fromJson(PublishedData.serializer())

            testRapid.inspektør.size shouldBeExactly 1

            actual shouldBe expected
        }

        test("Ved feil så publiseres feil på simba-rapid") {
            val expectedIncoming = mockForespoerselListeSvarMedFeil()

            val expected = mockFail(expectedIncoming)

            testRapid.sendJson(
                Pri.Key.BEHOV to Pri.BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE.toJson(Pri.BehovType.serializer()),
                Pri.Key.LØSNING to expectedIncoming.toJson(ForespoerselListeSvar.serializer()),
            )

            val actual = testRapid.firstMessage().readFail()

            testRapid.inspektør.size shouldBeExactly 1

            actual shouldBe expected
        }
    }) {
    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    private data class PublishedData(
        @JsonNames("@event_name")
        val eventName: EventName,
        val uuid: UUID,
        val data: Map<Key, JsonElement>,
    ) {
        companion object {
            fun mock(forespoerselListeSvar: ForespoerselListeSvar): PublishedData {
                val boomerangMap = forespoerselListeSvar.boomerang.toMap()

                val eventName = Key.EVENT_NAME.les(EventName.serializer(), boomerangMap)
                val transaksjonId = Key.UUID.les(UuidSerializer, boomerangMap)
                val data = boomerangMap[Key.DATA]?.toMap().orEmpty()

                val forespoersler = forespoerselListeSvar.resultat.associate { it.forespoerselId to it.toForespoersel() }

                return PublishedData(
                    eventName = eventName,
                    uuid = transaksjonId,
                    data =
                        data.plus(
                            Key.FORESPOERSLER_SVAR to
                                forespoersler.toJson(
                                    serializer = MapSerializer(UuidSerializer, Forespoersel.serializer()),
                                ),
                        ),
                )
            }
        }
    }
}

fun mockFail(forespoerselSvar: ForespoerselListeSvar): Fail {
    val feilmelding = "Klarte ikke hente forespørsler for vedtaksperiode-IDer. Ukjent feil."

    val boomerangMap = forespoerselSvar.boomerang.toMap()

    val eventName = Key.EVENT_NAME.les(EventName.serializer(), boomerangMap)
    val transaksjonId = Key.UUID.les(UuidSerializer, boomerangMap)
    val data = boomerangMap[Key.DATA]?.toMap().orEmpty()

    return Fail(
        feilmelding = feilmelding,
        event = eventName,
        transaksjonId = transaksjonId,
        forespoerselId = null,
        utloesendeMelding =
            mapOf(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.DATA to data.toJson(),
            ).toJson(),
    )
}
