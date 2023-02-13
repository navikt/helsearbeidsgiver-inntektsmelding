@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.NotisType
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.serializers.UuidSerializer
import no.nav.helsearbeidsgiver.felles.test.json.JsonIgnoreUnknown
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.lastMessageJson
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.pritopic.sendJson
import java.util.UUID

class ForespoerselMottattLøserTest : FunSpec({
    val testRapid = TestRapid()

    ForespoerselMottattLøser(testRapid)

    test("Ved notis om mottatt forespørsel publiseres notis om notifikasjon") {
        val expected = Published.mock()
        val forespoerselId = expected.uuid

        testRapid.sendJson(
            Pri.Key.NOTIS to Pri.NotisType.FORESPØRSEL_MOTTATT.toJson(),
            Pri.Key.ORGNR to expected.orgnrUnderenhet.toJson(),
            Pri.Key.FNR to expected.identitetsnummer.toJson(),
            Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson()
        )

        val actual = testRapid.lastMessageJson().let(Published::fromJson)

        testRapid.inspektør.size shouldBeExactly 1
        actual shouldBe expected
    }
})

@Serializable
private data class Published(
    val notis: List<NotisType>,
    val orgnrUnderenhet: String,
    val identitetsnummer: String,
    val uuid: UUID
) {
    companion object {
        fun mock(): Published =
            Published(
                notis = listOf(NotisType.NOTIFIKASJON_TRENGER_IM),
                orgnrUnderenhet = "certainly-stereo-facsimile",
                identitetsnummer = "resort-cringe-huddle",
                uuid = UUID.randomUUID()
            )

        fun fromJson(json: JsonElement): Published =
            JsonIgnoreUnknown.fromJson(serializer(), json)
    }
}
