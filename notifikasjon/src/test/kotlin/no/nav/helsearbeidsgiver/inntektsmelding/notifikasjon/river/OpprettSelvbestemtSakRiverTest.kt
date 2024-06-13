package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifySequence
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifkasjon.graphql.generated.enums.SaksStatus
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmeldingV1
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.db.SelvbestemtRepo
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class OpprettSelvbestemtSakRiverTest : FunSpec({

    val testRapid = TestRapid()
    val mockUrl = "selvbestemt-lenke"
    val mockSelvbestemtRepo = mockk<SelvbestemtRepo>()
    val mockagNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>()

    OpprettSelvbestemtSakRiver(mockUrl, mockSelvbestemtRepo, mockagNotifikasjonKlient).connect(testRapid)

    beforeTest {
        testRapid.reset()
        clearAllMocks()
    }

    test("opprett sak") {
        val sakId = UUID.randomUUID().toString()
        val innkommendeMelding = innkommendeMelding()

        coEvery { mockagNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any()) } returns sakId
        every { mockSelvbestemtRepo.lagreSakId(any(), any()) } returns 1

        testRapid.sendJson(innkommendeMelding.toMap())

        testRapid.inspektør.size shouldBeExactly 1

        testRapid.firstMessage().toMap() shouldContainExactly mapOf(
            Key.EVENT_NAME to innkommendeMelding.eventName.toJson(),
            Key.UUID to innkommendeMelding.transaksjonId.toJson(),
            Key.DATA to "".toJson(),
            Key.SAK_ID to sakId.toJson()
        )

        coVerifySequence {
            mockagNotifikasjonKlient.opprettNySak(
                virksomhetsnummer = innkommendeMelding.inntektsmelding.avsender.orgnr.verdi,
                merkelapp = "Inntektsmelding sykepenger",
                grupperingsid = innkommendeMelding.inntektsmelding.type.id.toString(),
                lenke = "$mockUrl/im-dialog/kvittering/agi/${innkommendeMelding.inntektsmelding.type.id}",
                tittel = "Inntektsmelding for ${innkommendeMelding.inntektsmelding.sykmeldt.navn}: " +
                    "f. ${innkommendeMelding.inntektsmelding.sykmeldt.fnr.verdi.take(6)}",
                statusTekst = "Mottatt - Se kvittering eller korriger inntektsmelding",
                initiellStatus = SaksStatus.FERDIG,
                harddeleteOm = any()
            )
            mockSelvbestemtRepo.lagreSakId(innkommendeMelding.inntektsmelding.type.id, sakId)
        }
    }

    context("håndterer feil") {

        test("fra klient") {
            val innkommendeMelding = innkommendeMelding()
            val forventetFail = innkommendeMelding.toFail()

            coEvery { mockagNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any()) } throws RuntimeException("RIP in peace")

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()
                .minus(Key.FORESPOERSEL_ID)
                .plus(Key.SELVBESTEMT_ID to innkommendeMelding.inntektsmelding.type.id.toJson())

            coVerifySequence {
                mockagNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any())
            }
            verify(exactly = 0) {
                mockSelvbestemtRepo.lagreSakId(any(), any())
            }
        }

        test("fra repo") {
            val innkommendeMelding = innkommendeMelding()
            val forventetFail = innkommendeMelding.toFail()

            coEvery { mockagNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any()) } returns UUID.randomUUID().toString()
            every { mockSelvbestemtRepo.lagreSakId(any(), any()) } throws RuntimeException("RIPperoni")

            testRapid.sendJson(innkommendeMelding.toMap())

            testRapid.inspektør.size shouldBeExactly 1

            testRapid.firstMessage().toMap() shouldContainExactly forventetFail.tilMelding()
                .minus(Key.FORESPOERSEL_ID)
                .plus(Key.SELVBESTEMT_ID to innkommendeMelding.inntektsmelding.type.id.toJson())

            coVerifySequence {
                mockagNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any())
                mockSelvbestemtRepo.lagreSakId(any(), any())
            }
        }
    }

    context("ignorerer melding") {
        withData(
            mapOf(
                "melding med uønsket behov" to Pair(Key.BEHOV, BehovType.VIRKSOMHET.toJson()),
                "melding med data" to Pair(Key.DATA, "".toJson()),
                "melding med fail" to Pair(Key.FAIL, mockFail.toJson(Fail.serializer()))
            )
        ) { uoensketKeyMedVerdi ->
            testRapid.sendJson(
                innkommendeMelding().toMap()
                    .plus(uoensketKeyMedVerdi)
            )

            testRapid.inspektør.size shouldBeExactly 0

            coVerify(exactly = 0) {
                mockagNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any())
                mockSelvbestemtRepo.lagreSakId(any(), any())
            }
        }
    }
})

private fun innkommendeMelding(): OpprettSelvbestemtSakMelding =
    OpprettSelvbestemtSakMelding(
        eventName = EventName.SELVBESTEMT_IM_MOTTATT,
        behovType = BehovType.OPPRETT_SELVBESTEMT_SAK,
        transaksjonId = UUID.randomUUID(),
        inntektsmelding = mockInntektsmeldingV1()
    )

private fun OpprettSelvbestemtSakMelding.toMap(): Map<Key, JsonElement> =
    mapOf(
        Key.EVENT_NAME to eventName.toJson(),
        Key.BEHOV to behovType.toJson(),
        Key.UUID to transaksjonId.toJson(),
        Key.SELVBESTEMT_INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer())
    )

private fun OpprettSelvbestemtSakMelding.toFail(): Fail =
    Fail(
        feilmelding = "Klarte ikke lagre sak for selvbestemt inntektsmelding.",
        event = EventName.SELVBESTEMT_IM_MOTTATT,
        transaksjonId = transaksjonId,
        forespoerselId = null,
        utloesendeMelding = toMap().toJson()
    )

private val mockFail = Fail(
    feilmelding = "I know kung-fu.",
    event = EventName.SELVBESTEMT_IM_MOTTATT,
    transaksjonId = UUID.randomUUID(),
    forespoerselId = null,
    utloesendeMelding = JsonNull
)
