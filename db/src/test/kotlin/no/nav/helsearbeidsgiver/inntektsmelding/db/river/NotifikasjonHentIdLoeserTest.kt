@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.db.ForespoerselRepository
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class NotifikasjonHentIdLoeserTest : FunSpec({
    val testRapid = TestRapid()
    val mockForespoerselRepo = mockk<ForespoerselRepository>()

    NotifikasjonHentIdLoeser(testRapid, mockForespoerselRepo)

    beforeEach {
        testRapid.reset()
        clearAllMocks()
    }

    test("Ved behov om å hente notifikasjon-ID-er publiseres ID-ene på samme event") {
        val expected = Published.mock()

        every { mockForespoerselRepo.hentSakId(any()) } returns expected.sakId
        every { mockForespoerselRepo.hentOppgaveId(any()) } returns expected.oppgaveId

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
            Key.BEHOV to BehovType.NOTIFIKASJON_HENT_ID.toJson(),
            Key.FORESPOERSEL_ID to expected.forespoerselId.toJson(),
            Key.TRANSACTION_ORIGIN to expected.transaksjonId.toJson()
        )

        testRapid.inspektør.size shouldBeExactly 1

        val actual = testRapid.firstMessage().fromJson(Published.serializer())

        actual shouldBe expected

        verifySequence {
            mockForespoerselRepo.hentSakId(any())
            mockForespoerselRepo.hentOppgaveId(any())
        }
    }

    test("Dersom sak eller oppgave-ID ikke finnes så republiseres den innkommende meldingen") {
        val expectedRepublisert = mapOf(
            Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
            Key.BEHOV to BehovType.NOTIFIKASJON_HENT_ID.toJson(),
            Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
            Key.TRANSACTION_ORIGIN to UUID.randomUUID().toJson()
        )

        every { mockForespoerselRepo.hentSakId(any()) } returns null
        every { mockForespoerselRepo.hentOppgaveId(any()) } returns null

        testRapid.sendJson(
            *expectedRepublisert.toList().toTypedArray()
        )

        val actual = testRapid.firstMessage()
            .fromJsonMapFiltered(Key.serializer())
            // Fjern nøkler vi ikke bryr oss om, som '@id'
            .filterKeys { expectedRepublisert.containsKey(it) }

        testRapid.inspektør.size shouldBeExactly 1

        actual shouldBe expectedRepublisert
    }

    test("Ved ukjent feil så republiseres den innkommende meldingen") {
        val expectedRepublisert = mapOf(
            Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
            Key.BEHOV to BehovType.NOTIFIKASJON_HENT_ID.toJson(),
            Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
            Key.TRANSACTION_ORIGIN to UUID.randomUUID().toJson()
        )

        every { mockForespoerselRepo.hentSakId(any()) } returns "en id for sak"
        every { mockForespoerselRepo.hentOppgaveId(any()) } throws RuntimeException("oppgaveId får du fikse sjæl!")

        testRapid.sendJson(
            *expectedRepublisert.toList().toTypedArray()
        )

        val actual = testRapid.firstMessage()
            .fromJsonMapFiltered(Key.serializer())
            // Fjern nøkler vi ikke bryr oss om, som '@id'
            .filterKeys { expectedRepublisert.containsKey(it) }

        testRapid.inspektør.size shouldBeExactly 1

        actual shouldBe expectedRepublisert
    }
})

@Serializable
private data class Published(
    @SerialName("@event_name")
    val eventName: EventName,
    @SerialName("sak_id")
    val sakId: String,
    @SerialName("oppgave_id")
    val oppgaveId: String,
    val forespoerselId: UUID,
    @SerialName("transaction_origin")
    val transaksjonId: UUID
) {
    companion object {
        fun mock(): Published =
            Published(
                eventName = EventName.FORESPOERSEL_BESVART,
                sakId = "syngende-hemul",
                oppgaveId = "skuffet-apokalypse",
                forespoerselId = UUID.randomUUID(),
                transaksjonId = UUID.randomUUID()
            )
    }
}
