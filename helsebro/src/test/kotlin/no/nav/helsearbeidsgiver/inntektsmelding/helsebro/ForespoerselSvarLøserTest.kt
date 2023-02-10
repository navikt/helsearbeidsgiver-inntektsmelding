@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.encodeToJsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.HentTrengerImLøsning
import no.nav.helsearbeidsgiver.felles.Key
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

    beforeTest {
        testRapid.reset()
    }

    withData(
        mapOf(
            "Ved suksessfull løsning på behov så publiseres løsning på simba-rapid" to mockForespoerselSvarMedSuksess(),
            "Ved feil så publiseres feil på simba-rapid" to mockForespoerselSvarMedFeil()
        )
    ) { expectedIncoming ->
        val expected = Published.mock(expectedIncoming)

        testRapid.sendJson(
            Pri.Key.BEHOV to ForespoerselSvar.behovType.toJson(),
            Pri.Key.LØSNING to expectedIncoming.let(Json::encodeToJsonElement)
        )

        val actual = testRapid.lastMessageJson().let(Published::fromJson)

        testRapid.inspektør.size shouldBeExactly 1
        actual shouldBe expected
    }

    test("Ved løsning med tom boomerang så publiseres ingenting på simba-rapid") {
        val expectedIncoming = mockForespoerselSvarMedSuksess()
            .copy(boomerang = emptyMap<String, Nothing>())

        testRapid.sendJson(
            Pri.Key.BEHOV to ForespoerselSvar.behovType.toJson(),
            Pri.Key.LØSNING to expectedIncoming.let(Json::encodeToJsonElement)
        )

        testRapid.inspektør.size shouldBeExactly 0
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
                løsning = mapOf(behovType to forespoerselSvar.toHentTrengerImLøsning()),
                uuid = forespoerselSvar.boomerang[Key.INITIATE_ID.str]
                    ?.fromJson(UuidSerializer)
                    .shouldNotBeNull()
            )

        fun fromJson(json: String): Published =
            JsonIgnoreUnknown.fromJson(serializer(), json)
    }
}
