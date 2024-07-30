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
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class OppgaveFerdigLoeserTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockAgNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>(relaxed = true)

        OppgaveFerdigLoeser(testRapid, mockAgNotifikasjonKlient)

        beforeEach {
            testRapid.reset()
            clearAllMocks()
        }

        test("Ved besvart forespørsel med oppgave-ID ferdigstilles oppgaven") {
            val expected = PublishedOppgave.mock()

            testRapid.sendJson(
                Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
                Key.OPPGAVE_ID to expected.oppgaveId.toJson(),
                Key.FORESPOERSEL_ID to expected.forespoerselId.toJson(),
                Key.UUID to expected.transaksjonId.toJson(),
            )

            val actual = testRapid.firstMessage().fromJson(PublishedOppgave.serializer())

            testRapid.inspektør.size shouldBeExactly 1

            actual shouldBe expected

            coVerifySequence {
                mockAgNotifikasjonKlient.oppgaveUtfoert(expected.oppgaveId)
            }
        }
    })

@Serializable
private data class PublishedOppgave(
    @SerialName("@event_name")
    val eventName: EventName,
    @SerialName("oppgave_id")
    val oppgaveId: String,
    val forespoerselId: UUID,
    @SerialName("uuid")
    val transaksjonId: UUID,
) {
    companion object {
        fun mock(): PublishedOppgave =
            PublishedOppgave(
                eventName = EventName.OPPGAVE_FERDIGSTILT,
                oppgaveId = "trist-kaleidoskop",
                forespoerselId = UUID.randomUUID(),
                transaksjonId = UUID.randomUUID(),
            )
    }
}
