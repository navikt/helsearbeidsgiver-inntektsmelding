@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import io.kotest.core.spec.style.FunSpec
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

    beforeEach {
        testRapid.reset()
    }

    test("Ved suksessfull løsning på behov så publiseres løsning på simba-rapid") {
        val expectedIncoming = mockForespoerselSvarMedSuksess()
        val expectedResultat = expectedIncoming.resultat.shouldNotBeNull()

        val expected = Published.mock(expectedIncoming)

        testRapid.sendJson(
            Pri.Key.LØSNING to Pri.BehovType.TRENGER_FORESPØRSEL.toJson(),
            Pri.Key.FORESPOERSEL_ID to expectedIncoming.forespoerselId.toJson(),
            Pri.Key.RESULTAT to mapOf(
                "orgnr" to expectedResultat.orgnr.toJson(),
                "fnr" to expectedResultat.fnr.toJson(),
                "sykmeldingsperioder" to expectedResultat.sykmeldingsperioder.let(Json::encodeToJsonElement),
                "forespurtData" to expectedResultat.forespurtData.let(Json::encodeToJsonElement)
            ).toJson(),
            Pri.Key.BOOMERANG to expectedIncoming.boomerang.toJson()
        )

        val actual = testRapid.lastMessageJson().let(Published::fromJson)

        testRapid.inspektør.size shouldBeExactly 1
        actual shouldBe expected
    }

    test("Ved feil så publiseres feil på simba-rapid") {
        val expectedIncoming = mockForespoerselSvarMedFeil()
        val expectedFeil = expectedIncoming.feil.shouldNotBeNull()

        val expected = Published.mock(expectedIncoming)

        testRapid.sendJson(
            Pri.Key.LØSNING to Pri.BehovType.TRENGER_FORESPØRSEL.toJson(),
            Pri.Key.FORESPOERSEL_ID to expectedIncoming.forespoerselId.toJson(),
            Pri.Key.FEIL to mapOf(
                "feilkode" to expectedFeil.feilkode.toJson(),
                "feilmelding" to expectedFeil.feilmelding.toJson()
            ).toJson(),
            Pri.Key.BOOMERANG to expectedIncoming.boomerang.toJson()
        )

        val actual = testRapid.lastMessageJson().let(Published::fromJson)

        testRapid.inspektør.size shouldBeExactly 1
        actual shouldBe expected
    }

    test("Ved løsning med tom boomerang så publiseres ingenting på simba-rapid") {
        val expectedIncoming = mockForespoerselSvarMedSuksess()
        val expectedResultat = expectedIncoming.resultat.shouldNotBeNull()

        testRapid.sendJson(
            Pri.Key.LØSNING to Pri.BehovType.TRENGER_FORESPØRSEL.toJson(),
            Pri.Key.FORESPOERSEL_ID to expectedIncoming.forespoerselId.toJson(),
            Pri.Key.RESULTAT to mapOf(
                "orgnr" to expectedResultat.orgnr.toJson(),
                "fnr" to expectedResultat.fnr.toJson(),
                "sykmeldingsperioder" to expectedResultat.sykmeldingsperioder.let(Json::encodeToJsonElement),
                "forespurt_data" to expectedResultat.forespurtData.let(Json::encodeToJsonElement)
            ).toJson(),
            Pri.Key.BOOMERANG to emptyMap<String, Nothing>().toJson()
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
