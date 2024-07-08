@file:UseSerializers(UuidSerializer::class)

package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.db.ForespoerselRepository
import no.nav.helsearbeidsgiver.utils.json.parseJson
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
        val expectedTransaksjonId: UUID = UUID.randomUUID()
        val expectedForespoerselId: UUID = UUID.randomUUID()
        val expectedOppgaveId = "syngende-hemul"
        val expectedSakId = "skuffet-apokalypse"

        every { mockForespoerselRepo.hentSakId(any()) } returns expectedSakId
        every { mockForespoerselRepo.hentOppgaveId(any()) } returns expectedOppgaveId

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
            Key.BEHOV to BehovType.NOTIFIKASJON_HENT_ID.toJson(),
            Key.UUID to expectedTransaksjonId.toJson(),
            Key.FORESPOERSEL_ID to expectedForespoerselId.toJson(),
        )

        testRapid.inspektør.size shouldBeExactly 2

        testRapid.firstMessage().toMap().also { sakMelding ->
            Key.EVENT_NAME.lesOrNull(EventName.serializer(), sakMelding) shouldBe EventName.FORESPOERSEL_BESVART
            Key.UUID.lesOrNull(UuidSerializer, sakMelding) shouldBe expectedTransaksjonId
            Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, sakMelding) shouldBe expectedForespoerselId
            Key.SAK_ID.lesOrNull(String.serializer(), sakMelding) shouldBe expectedSakId
        }

        testRapid.inspektør.message(1)
            .toString()
            .parseJson()
            .toMap()
            .also { oppgaveMelding ->
                Key.EVENT_NAME.lesOrNull(EventName.serializer(), oppgaveMelding) shouldBe EventName.FORESPOERSEL_BESVART
                Key.UUID.lesOrNull(UuidSerializer, oppgaveMelding) shouldBe expectedTransaksjonId
                Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, oppgaveMelding) shouldBe expectedForespoerselId
                Key.OPPGAVE_ID.lesOrNull(String.serializer(), oppgaveMelding) shouldBe expectedOppgaveId
            }

        verifySequence {
            mockForespoerselRepo.hentSakId(expectedForespoerselId)
            mockForespoerselRepo.hentOppgaveId(expectedForespoerselId)
        }
    }
})
