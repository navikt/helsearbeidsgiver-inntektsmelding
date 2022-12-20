package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equality.shouldBeEqualToComparingFields
import io.kotest.matchers.ints.shouldBeExactly
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.test.json.tryToJson
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.lastMessageJson
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import java.util.UUID

class ForespoerselMottattLøserTest : FunSpec({

//    val dataSource = Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver")

    val testRapid = TestRapid()

    ForespoerselMottattLøser(testRapid)

    test("Ved notis om mottatt forespørsel publiseres behov om notifikasjon") {
        val expected = Published(
            behov = listOf(BehovType.NOTIFIKASJON_TRENGER_IM),
            orgnrUnderenhet = "123",
            identitetsnummer = "abc",
            uuid = UUID.randomUUID().toString()
        )

        testRapid.sendJson(
            Key.NOTIS to BehovType.FORESPØRSEL_MOTTATT.tryToJson(),
            Key.ORGNR to expected.orgnrUnderenhet.tryToJson(),
            Key.FNR to expected.identitetsnummer.tryToJson(),
            Key.VEDTAKSPERIODE_ID to expected.uuid.tryToJson()
        )

        val actual = testRapid.lastMessageJson().let(Published::fromJson)

        testRapid.inspektør.size shouldBeExactly 1
        actual shouldBeEqualToComparingFields expected
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
        private val jsonBuilder = Json {
            ignoreUnknownKeys = true
        }

        fun fromJson(json: String): Published =
            jsonBuilder.decodeFromString(json)
    }
}
