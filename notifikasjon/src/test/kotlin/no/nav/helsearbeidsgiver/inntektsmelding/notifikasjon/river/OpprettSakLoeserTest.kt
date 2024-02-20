package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.OpprettNySakException
import no.nav.helsearbeidsgiver.arbeidsgivernotifkasjon.graphql.generated.enums.SaksStatus
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.januar
import java.util.UUID
import kotlin.time.Duration.Companion.days

class OpprettSakLoeserTest : FunSpec({

    val testRapid = TestRapid()
    val mockArbeidsgiverNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>()

    OpprettSakLoeser(testRapid, mockArbeidsgiverNotifikasjonKlient, "enSlagsUrl")

    beforeTest {
        testRapid.reset()
    }

    test("skal opprette sak med fullt navn") {
        val expectedSakId = "en helt særegen id"
        val forespoerselId = UUID.randomUUID()
        coEvery {
            mockArbeidsgiverNotifikasjonKlient.opprettNySak(
                virksomhetsnummer = "org-456",
                merkelapp = "Inntektsmelding",
                grupperingsid = forespoerselId.toString(),
                lenke = "enSlagsUrl/im-dialog/$forespoerselId",
                tittel = "Inntektsmelding for ${mockPersonDato().navn}: f. 050120",
                statusTekst = "NAV trenger inntektsmelding",
                initiellStatus = SaksStatus.UNDER_BEHANDLING,
                harddeleteOm = 150.days
            )
        } returns expectedSakId

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.SAK_OPPRETT_REQUESTED.toJson(),
            Key.BEHOV to BehovType.OPPRETT_SAK.toJson(),
            Key.ARBEIDSTAKER_INFORMASJON to mockPersonDato().toJson(PersonDato.serializer()),
            Key.ORGNRUNDERENHET to "org-456".toJson(),
            Key.IDENTITETSNUMMER to "12345678901".toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson()
        )

        val resultat = testRapid.firstMessage()

        resultat.toMap() shouldContainKey Key.DATA

        resultat.toMap().let {
            val actualSakId = it[Key.SAK_ID]?.fromJson(String.serializer())
            actualSakId shouldBe expectedSakId
        }
    }

    test("skal håndtere duplikatFeil og publisere feil") {

        val forespoerselId = UUID.randomUUID()
        coEvery {
            mockArbeidsgiverNotifikasjonKlient.opprettNySak(
                any(),
                any(),
                forespoerselId.toString(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } throws OpprettNySakException("Duplikat")

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.SAK_OPPRETT_REQUESTED.toJson(),
            Key.BEHOV to BehovType.OPPRETT_SAK.toJson(),
            Key.ARBEIDSTAKER_INFORMASJON to mockPersonDato().toJson(PersonDato.serializer()),
            Key.ORGNRUNDERENHET to "org-456".toJson(),
            Key.IDENTITETSNUMMER to "12345678901".toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson()
        )

        val resultat = testRapid.firstMessage()

        resultat.toMap() shouldContainKey Key.FAIL
    }
})

private fun mockPersonDato(): PersonDato =
    PersonDato(
        navn = "Rosa damesykkel",
        fødselsdato = 5.januar(2020),
        "12345678910"
    )
