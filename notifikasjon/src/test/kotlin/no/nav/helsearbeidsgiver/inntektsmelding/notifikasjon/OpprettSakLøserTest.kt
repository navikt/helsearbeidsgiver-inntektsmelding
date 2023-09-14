package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.json.fromJsonMapOnlyKeys
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.januar

class OpprettSakLøserTest : FunSpec({

    val testRapid = TestRapid()
    val mockArbeidsgiverNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>()

    OpprettSakLøser(testRapid, mockArbeidsgiverNotifikasjonKlient, "enSlagsUrl")

    beforeTest {
        testRapid.reset()
    }

    test("skal opprette sak med fullt navn") {
        val expectedSakId = "en helt særegen id"

        coEvery {
            mockArbeidsgiverNotifikasjonKlient.opprettNySak(
                "uuid-abc",
                "Inntektsmelding",
                "org-456",
                "Inntektsmelding for ${mockPersonDato().navn}: f. 050120",
                "enSlagsUrl/im-dialog/uuid-abc",
                "NAV trenger inntektsmelding",
                "P5M"
            )
        } returns expectedSakId

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.FORESPØRSEL_LAGRET.toJson(),
            Key.BEHOV to BehovType.OPPRETT_SAK.toJson(),
            DataFelt.ARBEIDSTAKER_INFORMASJON to mockPersonDato().toJson(PersonDato.serializer()),
            DataFelt.ORGNRUNDERENHET to "org-456".toJson(),
            Key.IDENTITETSNUMMER to "12345678901".toJson(),
            Key.FORESPOERSEL_ID to "uuid-abc".toJson()
        )

        val resultat = testRapid.firstMessage()

        resultat.fromJsonMapOnlyKeys() shouldContainKey Key.DATA

        resultat.fromJsonMapFiltered(DataFelt.serializer()).let {
            val actualSakId = it[DataFelt.SAK_ID]?.fromJson(String.serializer())
            actualSakId shouldBe expectedSakId
        }
    }
})

private fun mockPersonDato(): PersonDato =
    PersonDato(
        navn = "Rosa damesykkel",
        fødselsdato = 5.januar(2020),
        "12345678910"
    )
