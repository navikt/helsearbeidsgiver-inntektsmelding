package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonElement
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
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson

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
            testRapid.sendJson(
                Pri.Key.BEHOV to Pri.BehovType.TRENGER_FORESPØRSEL.toJson(Pri.BehovType.serializer()),
                Pri.Key.LOESNING to expectedIncoming.toJson(ForespoerselSvar.serializer()),
            )

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly mockSvar(expectedIncoming)
        }

        test("Ved feil så publiseres feil på simba-rapid") {
            val expectedIncoming = mockForespoerselSvarMedFeil()

            testRapid.sendJson(
                Pri.Key.BEHOV to Pri.BehovType.TRENGER_FORESPØRSEL.toJson(Pri.BehovType.serializer()),
                Pri.Key.LOESNING to expectedIncoming.toJson(ForespoerselSvar.serializer()),
            )

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().lesFail() shouldBe mockFail(expectedIncoming)
        }
    })

fun mockSvar(forespoerselSvar: ForespoerselSvar): Map<Key, JsonElement> {
    val boomerangMap = forespoerselSvar.boomerang.toMap()
    val data = boomerangMap[Key.DATA]?.toMap().orEmpty()

    return mapOf(
        Key.EVENT_NAME to boomerangMap[Key.EVENT_NAME],
        Key.KONTEKST_ID to boomerangMap[Key.KONTEKST_ID],
        Key.DATA to
            data
                .plus(
                    mapOf(
                        Key.FORESPOERSEL_ID to forespoerselSvar.forespoerselId.toJson(),
                        Key.FORESPOERSEL_SVAR to
                            forespoerselSvar.resultat
                                .shouldNotBeNull()
                                .toForespoersel()
                                .toJson(Forespoersel.serializer()),
                    ),
                ).toJson(),
    ).mapValuesNotNull { it }
}

fun mockFail(forespoerselSvar: ForespoerselSvar): Fail {
    val feilmelding = "Klarte ikke hente forespørsel. Feilet med kode '${forespoerselSvar.feil?.name.shouldNotBeNull()}'."

    val boomerangMap = forespoerselSvar.boomerang.toMap()

    val eventName = Key.EVENT_NAME.les(EventName.serializer(), boomerangMap)
    val transaksjonId = Key.KONTEKST_ID.les(UuidSerializer, boomerangMap)
    val data = boomerangMap[Key.DATA]?.toMap().orEmpty()

    return Fail(
        feilmelding = feilmelding,
        kontekstId = transaksjonId,
        utloesendeMelding =
            mapOf(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                Key.KONTEKST_ID to transaksjonId.toJson(),
                Key.DATA to data.toJson(),
            ),
    )
}
