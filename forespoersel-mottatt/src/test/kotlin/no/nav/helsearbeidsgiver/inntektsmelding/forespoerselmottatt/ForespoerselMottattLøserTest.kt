package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.test.json.JsonIgnoreUnknown
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.lastMessageJson
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.pritopic.sendJson
import java.util.UUID

class ForespoerselMottattLøserTest : FunSpec({

//    val dataSource = Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")

    val testRapid = TestRapid()

    ForespoerselMottattLøser(testRapid)

    test("Ved notis om mottatt forespørsel publiseres behov om notifikasjon") {
        val expected = Published.mock()

        testRapid.sendJson(
            Pri.Key.NOTIS to Pri.NotisType.FORESPØRSEL_MOTTATT.toJson(),
            Pri.Key.ORGNR to expected.orgnrUnderenhet.toJson(),
            Pri.Key.FNR to expected.identitetsnummer.toJson(),
            Pri.Key.VEDTAKSPERIODE_ID to expected.uuid.toJson()
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
    val behov: List<BehovType>,
    val orgnrUnderenhet: String,
    val identitetsnummer: String,
    val uuid: String
) {
    companion object {
        fun mock(): Published =
            Published(
                behov = listOf(BehovType.NOTIFIKASJON_TRENGER_IM),
                orgnrUnderenhet = "certainly-stereo-facsimile",
                identitetsnummer = "resort-cringe-huddle",
                uuid = UUID.randomUUID().toString()
            )

        fun fromJson(json: String): Published =
            JsonIgnoreUnknown.fromJson(serializer(), json)
    }
}
