@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
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
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.serializers.UuidSerializer
import no.nav.helsearbeidsgiver.felles.test.PublishedLøsning
import no.nav.helsearbeidsgiver.felles.test.json.JsonIgnoreUnknown
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.lastMessageJson
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.pritopic.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar

class ForespoerselSvarLøserTest : FunSpec({
    val testRapid = TestRapid()

    ForespoerselSvarLøser(testRapid)

    beforeTest {
        testRapid.reset()
    }

    withData(
        mapOf(
            "Ved suksessfull løsning på behov så publiseres løsning på simba-rapid" to mockForespoerselSvarMedSuksess(),
            "Ved suksessfull løsning med fastsatt inntekt på behov så publiseres løsning på simba-rapid" to mockForespoerselSvarMedSuksessMedFastsattInntekt(),
            "Ved feil så publiseres feil på simba-rapid" to mockForespoerselSvarMedFeil()
        )
    ) { expectedIncoming ->
        val expected = Published.mock(expectedIncoming)

        testRapid.sendJson(
            Pri.Key.BEHOV to ForespoerselSvar.behovType.toJson(Pri.BehovType.serializer()),
            Pri.Key.LØSNING to expectedIncoming.let(Json::encodeToJsonElement),
            Pri.Key.BOOMERANG to expectedIncoming.boomerang
        )

        val actual = testRapid.lastMessageJson().let(Published::fromJson)

        testRapid.inspektør.size shouldBeExactly 1
        actual shouldBe expected
    }
})

@Serializable
@OptIn(ExperimentalSerializationApi::class)
private data class Published(
    @JsonNames("@behov")
    override val behov: List<BehovType>,
    @JsonNames("@løsning")
    override val løsning: Map<BehovType, HentTrengerImLøsning>,
    val boomerang: JsonElement
) : PublishedLøsning {
    companion object {
        private val behovType = BehovType.HENT_TRENGER_IM

        fun mock(forespoerselSvar: ForespoerselSvar): Published =
            Published(
                behov = behovType.let(::listOf),
                løsning = mapOf(behovType to forespoerselSvar.toHentTrengerImLøsning()),
                boomerang = forespoerselSvar.boomerang
            )

        fun fromJson(json: JsonElement): Published =
            JsonIgnoreUnknown.fromJson(serializer(), json)
    }
}
