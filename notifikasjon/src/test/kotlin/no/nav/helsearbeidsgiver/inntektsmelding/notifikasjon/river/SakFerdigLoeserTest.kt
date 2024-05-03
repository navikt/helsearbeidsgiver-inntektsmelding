@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coVerifySequence
import io.mockk.mockk
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifkasjon.graphql.generated.enums.SaksStatus
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class SakFerdigLoeserTest : FunSpec({
    val testRapid = TestRapid()
    val mockAgNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>(relaxed = true)
    val linkUrl = "dummyurl"
    SakFerdigLoeser(testRapid, mockAgNotifikasjonKlient, linkUrl)

    beforeEach {
        testRapid.reset()
        clearAllMocks()
    }

    test("Ved besvart forespørsel med sak-ID så ferdigstilles saken") {
        val expected = PublishedSak.mock()

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
            Key.SAK_ID to expected.sakId.toJson(),
            Key.FORESPOERSEL_ID to expected.forespoerselId.toJson(),
            Key.UUID to expected.transaksjonId.toJson()
        )

        val actual = testRapid.firstMessage().fromJson(PublishedSak.serializer())

        testRapid.inspektør.size shouldBeExactly 1

        actual shouldBe expected

        coVerifySequence {
            mockAgNotifikasjonKlient.nyStatusSak(
                id = expected.sakId,
                status = SaksStatus.FERDIG,
                statusTekst = "Mottatt - Se kvittering eller korriger inntektsmelding",
                nyLenkeTilSak = "$linkUrl/im-dialog/kvittering/${actual.forespoerselId}"
            )
        }
    }
})

@Serializable
private data class PublishedSak(
    @SerialName("@event_name")
    val eventName: EventName,
    @SerialName("sak_id")
    val sakId: String,
    val forespoerselId: UUID,
    @SerialName("uuid")
    val transaksjonId: UUID
) {
    companion object {
        fun mock(): PublishedSak =
            PublishedSak(
                eventName = EventName.SAK_FERDIGSTILT,
                sakId = "sulten-kalamari",
                forespoerselId = UUID.randomUUID(),
                transaksjonId = UUID.randomUUID()
            )
    }
}
