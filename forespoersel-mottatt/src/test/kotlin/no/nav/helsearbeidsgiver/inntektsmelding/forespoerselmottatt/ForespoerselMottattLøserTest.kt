@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.pritopic.sendJson
import java.util.UUID

class ForespoerselMottattLøserTest : FunSpec({
    val testRapid = TestRapid()

    ForespoerselMottattLøser(testRapid)

    test("Ved notis om mottatt forespørsel publiseres behov om notifikasjon") {
        val expected = Published.mock()
        val forespoerselId = expected.forespoerselId!!
        testRapid.sendJson(
            Pri.Key.NOTIS to Pri.NotisType.FORESPØRSEL_MOTTATT.toJson(Pri.NotisType.serializer()),
            Pri.Key.ORGNR to expected.orgnrUnderenhet.toJson(),
            Pri.Key.FNR to expected.identitetsnummer.toJson(),
            Pri.Key.FORESPOERSEL_ID to forespoerselId.toJson()
        )

        val actual = testRapid.firstMessage().fromJson(Published.serializer())

        testRapid.inspektør.size shouldBeExactly 1
        actual shouldBe expected
    }
})

@Serializable
private data class Published(
    @SerialName("@behov")
    val behov: BehovType,
    val orgnrUnderenhet: String,
    val identitetsnummer: String,
    val forespoerselId: UUID?
) {
    companion object {
        fun mock(): Published =
            Published(
                behov = BehovType.LAGRE_FORESPOERSEL,
                orgnrUnderenhet = "certainly-stereo-facsimile",
                identitetsnummer = "resort-cringe-huddle",
                forespoerselId = UUID.randomUUID()
            )
    }
}
