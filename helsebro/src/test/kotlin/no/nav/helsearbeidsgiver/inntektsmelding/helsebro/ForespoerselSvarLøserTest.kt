@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.encodeToJsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.HentTrengerImLøsning
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.serializers.UuidSerializer
import no.nav.helsearbeidsgiver.felles.test.PublishedLøsning
import no.nav.helsearbeidsgiver.felles.test.json.JsonIgnoreUnknown
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.lastMessageJson
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.pritopic.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import java.util.UUID

class ForespoerselSvarLøserTest : FunSpec({
    val testRapid = TestRapid()

    ForespoerselSvarLøser(testRapid)

    test("Ved løsning på behov så publiseres løsning på simba-rapid") {
        val expectedIncoming = mockForespoerselSvar()

        val expected = Published.mock(expectedIncoming)

        testRapid.sendJson(
            Pri.Key.LØSNING to Pri.BehovType.TRENGER_FORESPØRSEL.toJson(),
            Pri.Key.ORGNR to expectedIncoming.orgnr.toJson(),
            Pri.Key.FNR to expectedIncoming.fnr.toJson(),
            Pri.Key.FORESPOERSEL_ID to expectedIncoming.forespoerselId.toJson(),
            Pri.Key.SYKMELDINGSPERIODER to expectedIncoming.sykmeldingsperioder.let(Json::encodeToJsonElement),
            Pri.Key.FORESPURT_DATA to expectedIncoming.forespurtData.let(Json::encodeToJsonElement),
            Pri.Key.BOOMERANG to expectedIncoming.boomerang.toJson()
        )

        val actual = testRapid.lastMessageJson().let(Published::fromJson)

        testRapid.inspektør.size shouldBeExactly 1
        actual shouldBe expected
    }

    test("Ved løsning med tom boomerang så kastes BoomerangContentException") {
        val expectedIncoming = mockForespoerselSvar()

        shouldThrowExactly<BoomerangContentException> {
            testRapid.sendJson(
                Pri.Key.LØSNING to Pri.BehovType.TRENGER_FORESPØRSEL.toJson(),
                Pri.Key.ORGNR to expectedIncoming.orgnr.toJson(),
                Pri.Key.FNR to expectedIncoming.fnr.toJson(),
                Pri.Key.FORESPOERSEL_ID to expectedIncoming.forespoerselId.toJson(),
                Pri.Key.SYKMELDINGSPERIODER to expectedIncoming.sykmeldingsperioder.let(Json::encodeToJsonElement),
                Pri.Key.FORESPURT_DATA to expectedIncoming.forespurtData.let(Json::encodeToJsonElement),
                Pri.Key.BOOMERANG to emptyMap<String, JsonElement>().toJson()
            )
        }
    }
})

@Serializable
@OptIn(ExperimentalSerializationApi::class)
private data class Published(
    @JsonNames("@behov")
    override val behov: List<BehovType>,
    @JsonNames("@løsning")
    override val løsning: Map<BehovType, HentTrengerImLøsning>,
    val uuid: UUID
) : PublishedLøsning {
    companion object {
        private val behovType = BehovType.HENT_TRENGER_IM

        fun mock(forespoerselSvar: ForespoerselSvar): Published =
            Published(
                behov = behovType.let(::listOf),
                løsning = mapOf(
                    behovType to
                        HentTrengerImLøsning(
                            value = TrengerInntekt(
                                orgnr = forespoerselSvar.orgnr,
                                fnr = forespoerselSvar.fnr,
                                sykmeldingsperioder = forespoerselSvar.sykmeldingsperioder,
                                forespurtData = forespoerselSvar.forespurtData
                            )
                        )
                ),
                uuid = forespoerselSvar.boomerang[Key.INITIATE_ID.str]
                    ?.fromJson(UuidSerializer)
                    .shouldNotBeNull()
            )

        fun fromJson(json: String): Published =
            JsonIgnoreUnknown.fromJson(serializer(), json)
    }
}
