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
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
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
        val expected = Behov.create(
            EventName.FORESPOERSEL_BESVART,
            BehovType.NOTIFIKASJON_HENT_ID,
            UUID.randomUUID().toString(),
            mapOf(
                Key.TRANSACTION_ORIGIN to UUID.randomUUID(),
                DataFelt.OPPGAVE_ID to "syngende-hemul",
                DataFelt.SAK_ID to "skuffet-apokalypse"
            )
        ) {
            it.interestedIn(DataFelt.OPPGAVE_ID, DataFelt.SAK_ID, Key.TRANSACTION_ORIGIN)
        }

        every { mockForespoerselRepo.hentSakId(any()) } returns expected[DataFelt.SAK_ID].asText()
        every { mockForespoerselRepo.hentOppgaveId(any()) } returns expected[DataFelt.OPPGAVE_ID].asText()

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
            Key.BEHOV to BehovType.NOTIFIKASJON_HENT_ID.toJson(),
            Key.FORESPOERSEL_ID to expected.forespoerselId!!.toJson(),
            Key.TRANSACTION_ORIGIN to expected[Key.TRANSACTION_ORIGIN].asText().toJson()
        )

        testRapid.inspektør.size shouldBeExactly 1

        val actual = testRapid.firstMessage().toMap()

        actual[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer)?.toString() shouldBe expected.forespoerselId
        actual[Key.UUID]?.fromJson(UuidSerializer)?.toString().orEmpty() shouldBe expected.uuid()

        verifySequence {
            mockForespoerselRepo.hentSakId(any())
            mockForespoerselRepo.hentOppgaveId(any())
        }
    }

    xtest("Dersom sak eller oppgave-ID ikke finnes så republiseres den innkommende meldingen") {
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
