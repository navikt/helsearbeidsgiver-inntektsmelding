package no.nav.helsearbeidsgiver.inntektsmelding.inntekt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Fail
import no.nav.helsearbeidsgiver.felles.Fnr
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.InntektPerMaaned
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Orgnr
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Data
import no.nav.helsearbeidsgiver.felles.test.json.toDomeneMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.felles.utils.toYearMonth
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.desember
import no.nav.helsearbeidsgiver.utils.test.date.november
import no.nav.helsearbeidsgiver.utils.test.date.oktober
import java.time.YearMonth
import java.util.UUID

class InntektLoeserTest : FunSpec({

    val testRapid = TestRapid()
    val inntektKlient = mockk<InntektKlient>()

    InntektLoeser(testRapid, inntektKlient)

    beforeTest {
        testRapid.reset()
        clearAllMocks()
    }

    test("Gir inntekt når klienten svarer med inntekt for orgnr") {
        coEvery {
            inntektKlient.hentInntektPerOrgnrOgMaaned(any(), any(), any(), any(), any())
        } returns mapOf(
            Mock.orgnr.verdi to mapOf(
                oktober(1990) to 10.0,
                november(1990) to 11.0,
                desember(1990) to 12.0
            ),
            "annet orgnr" to mapOf(
                oktober(1990) to 20.0,
                november(1990) to 21.0,
                desember(1990) to 22.0
            )
        )

        testRapid.sendJson(*mockInnkommendeMelding())

        val publisert = testRapid.firstMessage().toDomeneMessage<Data>() {
            it.interestedIn(DataFelt.INNTEKT)
        }

        publisert[DataFelt.INNTEKT].toString().fromJson(Inntekt.serializer()) shouldBe Inntekt(
            maanedOversikt = listOf(
                InntektPerMaaned(
                    maaned = oktober(1990),
                    inntekt = 10.0
                ),
                InntektPerMaaned(
                    maaned = november(1990),
                    inntekt = 11.0
                ),
                InntektPerMaaned(
                    maaned = desember(1990),
                    inntekt = 12.0
                )
            )
        )
    }

    test("Gir _tom_ inntekt når klienten svarer med inntekt utelukkende for andre orgnr") {
        coEvery {
            inntektKlient.hentInntektPerOrgnrOgMaaned(any(), any(), any(), any(), any())
        } returns mapOf(
            "annet orgnr" to mapOf(
                oktober(1990) to 20.0,
                november(1990) to 21.0,
                desember(1990) to 22.0
            )
        )

        testRapid.sendJson(*mockInnkommendeMelding())

        val publisert = testRapid.firstMessage().toDomeneMessage<Data>() {
            it.interestedIn(DataFelt.INNTEKT)
        }

        publisert[DataFelt.INNTEKT].toString().fromJson(Inntekt.serializer()) shouldBe Inntekt(emptyList())
    }

    test("Svarer med påkrevde felt") {
        coEvery {
            inntektKlient.hentInntektPerOrgnrOgMaaned(any(), any(), any(), any(), any())
        } returns emptyMap()

        testRapid.sendJson(*mockInnkommendeMelding())

        val publisert = testRapid.firstMessage().toMap()

        publisert shouldNotContainKey Key.FAIL

        publisert shouldContainKey Key.EVENT_NAME
        publisert shouldContainKey Key.DATA
        publisert shouldContainKey Key.UUID
        publisert shouldContainKey DataFelt.INNTEKT
    }

    test("Kall mot klient bruker korrekte verdier lest fra innkommende melding") {
        coEvery {
            inntektKlient.hentInntektPerOrgnrOgMaaned(any(), any(), any(), any(), any())
        } returns emptyMap()

        val innkommendeMelding = mockInnkommendeMelding()

        testRapid.sendJson(*innkommendeMelding)

        coVerifySequence {
            inntektKlient.hentInntektPerOrgnrOgMaaned(
                fnr = Mock.fnr.verdi,
                fom = Mock.skjaeringstidspunkt.toYearMonth().minusMonths(3),
                tom = Mock.skjaeringstidspunkt.toYearMonth().minusMonths(1),
                navConsumerId = any(),
                callId = any()
            )
        }
    }

    test("Feil fra klienten gir feilmelding på rapid") {
        coEvery {
            inntektKlient.hentInntektPerOrgnrOgMaaned(any(), any(), any(), any(), any())
        } throws RuntimeException()

        testRapid.sendJson(*mockInnkommendeMelding())

        coVerifySequence {
            inntektKlient.hentInntektPerOrgnrOgMaaned(any(), any(), any(), any(), any())
        }

        val publisert = testRapid.firstMessage().toDomeneMessage<no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail>()

        publisert.feilmelding shouldBe "Klarte ikke hente inntekt."
    }

    test("Feil i innkommende melding gir feilmelding på rapid") {
        mockInnkommendeMelding()
            .plus(DataFelt.FNR to "ikke et fnr".toJson())
            .let(testRapid::sendJson)

        coVerify(exactly = 0) {
            inntektKlient.hentInntektPerOrgnrOgMaaned(any(), any(), any(), any(), any())
        }

        val publisert = testRapid.firstMessage().toDomeneMessage<no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail>()

        publisert.feilmelding shouldBe "Klarte ikke lese påkrevde felt fra innkommende melding."
    }

    test("Ukjent feil gir feilmelding på rapid") {
        val mockInntektPerOgnrOgMaaned = mockk<Map<String, Map<YearMonth, Double>>>()

        coEvery {
            inntektKlient.hentInntektPerOrgnrOgMaaned(any(), any(), any(), any(), any())
        } returns mockInntektPerOgnrOgMaaned

        every { mockInntektPerOgnrOgMaaned[any()] } throws RuntimeException()

        testRapid.sendJson(*mockInnkommendeMelding())

        coVerifySequence {
            inntektKlient.hentInntektPerOrgnrOgMaaned(any(), any(), any(), any(), any())
        }

        val publisert = testRapid.firstMessage().toDomeneMessage<no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail>()

        publisert.feilmelding shouldBe "Ukjent feil."
    }
})

private object Mock {
    val uuid: UUID = UUID.randomUUID()
    val orgnr = Orgnr("123456785")
    val fnr = Fnr("10107400000")
    val skjaeringstidspunkt = 14.april
}

private fun mockInnkommendeMelding(): Array<Pair<IKey, JsonElement>> =
    arrayOf(
        Key.EVENT_NAME to EventName.INNTEKT_REQUESTED.toJson(),
        Key.BEHOV to BehovType.INNTEKT.toJson(),
        Key.UUID to Mock.uuid.toJson(),
        DataFelt.ORGNRUNDERENHET to Mock.orgnr.verdi.toJson(),
        DataFelt.FNR to Mock.fnr.verdi.toJson(),
        DataFelt.SKJAERINGSTIDSPUNKT to Mock.skjaeringstidspunkt.toJson()
    )
