package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.db.SelvbestemtImRepo
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.juli
import java.util.UUID

class LagreSelvbestemtImRiverTest : FunSpec({
    val testRapid = TestRapid()
    val mockSelvbestemtImRepo = mockk<SelvbestemtImRepo>()

    LagreSelvbestemtImRiver(mockSelvbestemtImRepo).connect(testRapid)

    beforeTest {
        testRapid.reset()
        clearAllMocks()
    }

    context("selvbestemt inntektsmelding lagres") {
        val inntektsmeldingId = UUID.randomUUID()

        withData(
            mapOf(
                "hvis ingen andre inntektsmeldinger er mottatt" to null,
                "hvis ikke duplikat av tidligere inntektsmeldinger" to mockInntektsmeldingV1().copy(
                    id = inntektsmeldingId,
                    sykmeldingsperioder = listOf(13.juli til 31.juli)
                )
            )
        ) { eksisterendeIm ->
            every { mockSelvbestemtImRepo.hentNyesteIm(any()) } returns eksisterendeIm
            every { mockSelvbestemtImRepo.lagreIm(any()) } just Runs

            val nyInntektsmelding = mockInntektsmeldingV1().copy(id = inntektsmeldingId)

            val innkommendeMelding = LagreSelvbestemtImMelding(
                eventName = EventName.SELVBESTEMT_IM_MOTTATT,
                behovType = BehovType.LAGRE_SELVBESTEMT_IM,
                transaksjonId = UUID.randomUUID(),
                selvbestemtInntektsmelding = nyInntektsmelding
            )

            testRapid.sendJson(innkommendeMelding.tilMap())

            testRapid.inspektør.size shouldBeExactly 1

            val publisert = testRapid.firstMessage().toMap()

            Key.EVENT_NAME.lesOrNull(EventName.serializer(), publisert) shouldBe innkommendeMelding.eventName
            Key.UUID.lesOrNull(UuidSerializer, publisert) shouldBe innkommendeMelding.transaksjonId
            Key.DATA.lesOrNull(String.serializer(), publisert) shouldBe ""
            Key.SELVBESTEMT_INNTEKTSMELDING.lesOrNull(Inntektsmelding.serializer(), publisert) shouldBe nyInntektsmelding
            Key.ER_DUPLIKAT_IM.lesOrNull(Boolean.serializer(), publisert) shouldBe false

            publisert[Key.BEHOV].shouldBeNull()
            publisert[Key.FAIL].shouldBeNull()

            verifySequence {
                mockSelvbestemtImRepo.hentNyesteIm(nyInntektsmelding.type.id)
                mockSelvbestemtImRepo.lagreIm(nyInntektsmelding)
            }
        }
    }

    test("duplikat lagres ikke, men svarer OK") {
        val nyInntektsmelding = mockInntektsmeldingV1()

        val duplikatIm = nyInntektsmelding.copy(
            avsender = nyInntektsmelding.avsender.copy(
                fnr = "03067211111",
                navn = "Intens Delfia",
                tlf = "35350404"
            ),
            aarsakInnsending = AarsakInnsending.Ny,
            mottatt = nyInntektsmelding.mottatt.minusDays(14)
        )

        every { mockSelvbestemtImRepo.hentNyesteIm(any()) } returns duplikatIm
        every { mockSelvbestemtImRepo.lagreIm(any()) } just Runs

        val innkommendeMelding = LagreSelvbestemtImMelding(
            eventName = EventName.SELVBESTEMT_IM_MOTTATT,
            behovType = BehovType.LAGRE_SELVBESTEMT_IM,
            transaksjonId = UUID.randomUUID(),
            selvbestemtInntektsmelding = nyInntektsmelding
        )

        testRapid.sendJson(innkommendeMelding.tilMap())

        testRapid.inspektør.size shouldBeExactly 1

        val publisert = testRapid.firstMessage().toMap()

        Key.EVENT_NAME.lesOrNull(EventName.serializer(), publisert) shouldBe innkommendeMelding.eventName
        Key.UUID.lesOrNull(UuidSerializer, publisert) shouldBe innkommendeMelding.transaksjonId
        Key.DATA.lesOrNull(String.serializer(), publisert) shouldBe ""
        Key.SELVBESTEMT_INNTEKTSMELDING.lesOrNull(Inntektsmelding.serializer(), publisert) shouldBe nyInntektsmelding
        Key.ER_DUPLIKAT_IM.lesOrNull(Boolean.serializer(), publisert) shouldBe true

        publisert[Key.BEHOV].shouldBeNull()
        publisert[Key.FAIL].shouldBeNull()

        verifySequence {
            mockSelvbestemtImRepo.hentNyesteIm(nyInntektsmelding.type.id)
        }
        verify(exactly = 0) {
            mockSelvbestemtImRepo.lagreIm(nyInntektsmelding)
        }
    }

    test("håndterer at repo feiler") {
        every {
            mockSelvbestemtImRepo.hentNyesteIm(any())
        } throws RuntimeException("fy fasiken, den svei")

        val innkommendeMelding = LagreSelvbestemtImMelding(
            eventName = EventName.SELVBESTEMT_IM_MOTTATT,
            behovType = BehovType.LAGRE_SELVBESTEMT_IM,
            transaksjonId = UUID.randomUUID(),
            selvbestemtInntektsmelding = mockInntektsmeldingV1()
        )

        val innkommendeJsonMap = innkommendeMelding.tilMap()

        val forventetFail = Fail(
            feilmelding = "Klarte ikke lagre selvbestemt inntektsmelding.",
            event = innkommendeMelding.eventName,
            transaksjonId = innkommendeMelding.transaksjonId,
            forespoerselId = null,
            utloesendeMelding = innkommendeJsonMap.toJson()
        )

        testRapid.sendJson(innkommendeJsonMap)

        testRapid.inspektør.size shouldBeExactly 1

        val publisert = testRapid.firstMessage().toMap()

        Key.FAIL.lesOrNull(Fail.serializer(), publisert) shouldBe forventetFail
        Key.EVENT_NAME.lesOrNull(EventName.serializer(), publisert) shouldBe innkommendeMelding.eventName
        Key.UUID.lesOrNull(UuidSerializer, publisert) shouldBe innkommendeMelding.transaksjonId
        Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, publisert).shouldBeNull()

        publisert[Key.BEHOV].shouldBeNull()
        publisert[Key.DATA].shouldBeNull()

        verifySequence {
            mockSelvbestemtImRepo.hentNyesteIm(any())
        }
        verify(exactly = 0) {
            mockSelvbestemtImRepo.lagreIm(any())
        }
    }

    context("ignorerer melding") {
        withData(
            mapOf(
                "melding med data" to Pair(Key.DATA, "".toJson()),
                "melding med fail" to Pair(Key.FAIL, mockFail.toJson(Fail.serializer()))
            )
        ) { uoensketKeyMedVerdi ->
            val innkommendeMelding = LagreSelvbestemtImMelding(
                eventName = EventName.SELVBESTEMT_IM_MOTTATT,
                behovType = BehovType.LAGRE_SELVBESTEMT_IM,
                transaksjonId = UUID.randomUUID(),
                selvbestemtInntektsmelding = mockInntektsmeldingV1()
            )

            testRapid.sendJson(
                innkommendeMelding.tilMap()
                    .plus(uoensketKeyMedVerdi)
            )

            testRapid.inspektør.size shouldBeExactly 0

            verify(exactly = 0) {
                mockSelvbestemtImRepo.hentNyesteIm(any())
                mockSelvbestemtImRepo.lagreIm(any())
            }
        }

        test("melding med uønsket behov") {
            val innkommendeMelding = LagreSelvbestemtImMelding(
                eventName = EventName.SELVBESTEMT_IM_MOTTATT,
                behovType = BehovType.VIRKSOMHET,
                transaksjonId = UUID.randomUUID(),
                selvbestemtInntektsmelding = mockInntektsmeldingV1()
            )

            testRapid.sendJson(innkommendeMelding.tilMap())

            testRapid.inspektør.size shouldBeExactly 0

            verify(exactly = 0) {
                mockSelvbestemtImRepo.hentNyesteIm(any())
                mockSelvbestemtImRepo.lagreIm(any())
            }
        }
    }
})

private fun LagreSelvbestemtImMelding.tilMap(): Map<Key, JsonElement> =
    mapOf(
        Key.EVENT_NAME to eventName.toJson(),
        Key.BEHOV to behovType.toJson(),
        Key.UUID to transaksjonId.toJson(),
        Key.SELVBESTEMT_INNTEKTSMELDING to selvbestemtInntektsmelding.toJson(Inntektsmelding.serializer())
    )

private val mockFail = Fail(
    feilmelding = "Vi har et KJEMPEPROBLEM!",
    event = EventName.SELVBESTEMT_IM_MOTTATT,
    transaksjonId = UUID.randomUUID(),
    forespoerselId = UUID.randomUUID(),
    utloesendeMelding = JsonNull
)
