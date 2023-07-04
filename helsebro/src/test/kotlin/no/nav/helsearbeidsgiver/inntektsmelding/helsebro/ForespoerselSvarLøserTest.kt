@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.HentTrengerImLøsning
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.pritopic.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.test.mockForespoerselSvarMedFeil
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.test.mockForespoerselSvarMedSuksess
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.test.mockForespoerselSvarMedSuksessMedFastsattInntekt
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson

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
            Pri.Key.LØSNING to expectedIncoming.toJson(ForespoerselSvar.serializer()),
            Pri.Key.BOOMERANG to expectedIncoming.boomerang
        )

        val actual = testRapid.firstMessage().fromJson(Published.serializer())

        testRapid.inspektør.size shouldBeExactly 2
        actual shouldBe expected
    }
})

@Serializable
@OptIn(ExperimentalSerializationApi::class)
private data class Published(
    @JsonNames("@behov")
    val behov: List<BehovType>,
    @JsonNames("@løsning")
    val løsning: Map<BehovType, HentTrengerImLøsning>,
    val boomerang: JsonElement
) {
    companion object {
        private val behovType = BehovType.HENT_TRENGER_IM

        fun mock(forespoerselSvar: ForespoerselSvar): Published =
            Published(
                behov = behovType.let(::listOf),
                løsning = mapOf(behovType to forespoerselSvar.toHentTrengerImLøsning()),
                boomerang = forespoerselSvar.boomerang
            )
    }
}
