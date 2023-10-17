package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.json.fromJsonMapOnlyDatafelter
import no.nav.helsearbeidsgiver.felles.test.json.fromJsonMapOnlyKeys
import no.nav.helsearbeidsgiver.felles.test.mock.mockTrengerInntekt
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.fromJsonToString
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotifikasjonTrengerInntektMeldingIT : EndToEndTest() {

    @Test
    fun `Oppretter og lagrer sak etter at forespørselen er mottatt`() {
        coEvery {
            arbeidsgiverNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any())
        } returns Mock.SAK_ID

        publish(
            Key.EVENT_NAME to EventName.FORESPØRSEL_LAGRET.toJson(),
            Key.IDENTITETSNUMMER to Mock.FNR.toJson(),
            DataFelt.ORGNRUNDERENHET to Mock.ORGNR.toJson(),
            Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson()
        )

        Thread.sleep(10000)

        messages.filter(EventName.FORESPØRSEL_LAGRET)
            .filter(BehovType.FULLT_NAVN)
            .first()
            .fromJsonMapOnlyKeys()
            .also {
                it[Key.IDENTITETSNUMMER]?.fromJsonToString() shouldBe Mock.FNR
                it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
            }

        messages.filter(EventName.FORESPØRSEL_LAGRET)
            .filter(DataFelt.ARBEIDSTAKER_INFORMASJON)
            .first()
            .fromJsonMapOnlyDatafelter()
            .also {
                it[DataFelt.ARBEIDSTAKER_INFORMASJON]
                    ?.fromJson(PersonDato.serializer())
                    .shouldNotBeNull()
            }

        messages.filter(EventName.FORESPØRSEL_LAGRET)
            .filter(BehovType.OPPRETT_SAK)
            .first()
            .fromJsonMapOnlyKeys()
            .also {
                it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
            }

        messages.filter(EventName.FORESPØRSEL_LAGRET)
            .filter(DataFelt.SAK_ID)
            .first()
            .also {
                val sakId = it.fromJsonMapOnlyDatafelter()[DataFelt.SAK_ID]?.fromJsonToString()

                sakId shouldBe Mock.SAK_ID

                val forespoerselId = it.fromJsonMapOnlyKeys()[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer)

                forespoerselId shouldBe Mock.forespoerselId
            }

        messages.filter(EventName.SAK_OPPRETTET)
            .first()
            .fromJsonMapOnlyDatafelter()
            .also {
                it[DataFelt.SAK_ID]?.fromJsonToString() shouldBe Mock.SAK_ID
            }
    }

    @Test
    fun `Oppretter og lagrer oppgave etter at forespørselen er mottatt`() {
        coEvery {
            arbeidsgiverNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Mock.OPPGAVE_ID

        publish(
            Key.EVENT_NAME to EventName.FORESPØRSEL_LAGRET.toJson(),
            DataFelt.ORGNRUNDERENHET to Mock.ORGNR.toJson(),
            Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson()
        )

        Thread.sleep(8000)

        var transaksjonsId: String

        messages.filter(EventName.FORESPØRSEL_LAGRET)
            .filter(BehovType.OPPRETT_OPPGAVE)
            .first()
            .also { msg ->
                val msgOnlyKeys = msg.fromJsonMapOnlyKeys()

                msgOnlyKeys[Key.UUID]
                    .shouldNotBeNull()
                    .fromJsonToString()
                    .also { id -> transaksjonsId = id }

                msgOnlyKeys[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId

                val orgnr = msg.fromJsonMapOnlyDatafelter()[DataFelt.ORGNRUNDERENHET]?.fromJsonToString()

                orgnr shouldBe Mock.ORGNR
            }

        messages.filter(EventName.FORESPØRSEL_LAGRET)
            .filter(BehovType.PERSISTER_OPPGAVE_ID)
            .first()
            .also {
                val oppgaveId = it.fromJsonMapOnlyDatafelter()
                    .get(DataFelt.OPPGAVE_ID)
                    ?.fromJsonToString()

                oppgaveId shouldBe Mock.OPPGAVE_ID

                val msgKeyValues = it.fromJsonMapOnlyKeys()

                msgKeyValues[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
                msgKeyValues[Key.UUID]?.fromJsonToString() shouldBe transaksjonsId
            }

        messages.filter(EventName.OPPGAVE_LAGRET)
            .first()
            .also {
                val oppgaveId = it.fromJsonMapOnlyDatafelter()
                    .get(DataFelt.OPPGAVE_ID)
                    ?.fromJsonToString()

                oppgaveId shouldBe Mock.OPPGAVE_ID

                it.fromJsonMapOnlyKeys() shouldNotContainKey Key.UUID
            }
    }

    @Test
    fun `Oppretter og lagrer sak ved manuell rekjøring`() {
        var transactionId: UUID

        coEvery {
            arbeidsgiverNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any())
        } returns Mock.SAK_ID

        publish(
            Key.EVENT_NAME to EventName.MANUELL_OPPRETT_SAK_REQUESTED.toJson(),
            Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson()
        )

        Thread.sleep(10000)

        messages.filter(EventName.MANUELL_OPPRETT_SAK_REQUESTED)
            .filter(BehovType.HENT_TRENGER_IM)
            .first()
            .toMap()
            .also {
                it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
                transactionId = it[Key.UUID]?.fromJson(UuidSerializer).shouldNotBeNull()
            }

        publish(
            Key.EVENT_NAME to EventName.MANUELL_OPPRETT_SAK_REQUESTED.toJson(),
            Key.DATA to "".toJson(),
            Key.UUID to transactionId.toJson(),
            DataFelt.FORESPOERSEL_SVAR to mockTrengerInntekt().copy(fnr = Mock.FNR, orgnr = Mock.ORGNR).toJson(TrengerInntekt.serializer())
        )

        Thread.sleep(8000)

        messages.filter(EventName.MANUELL_OPPRETT_SAK_REQUESTED)
            .filter(BehovType.FULLT_NAVN)
            .first()
            .toMap()
            .also {
                it[Key.IDENTITETSNUMMER]?.fromJsonToString() shouldBe Mock.FNR
                it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
            }

        messages.filter(EventName.MANUELL_OPPRETT_SAK_REQUESTED)
            .filter(DataFelt.ARBEIDSTAKER_INFORMASJON)
            .first()
            .fromJsonMapOnlyDatafelter()
            .also {
                it[DataFelt.ARBEIDSTAKER_INFORMASJON]
                    ?.fromJson(PersonDato.serializer())
                    .shouldNotBeNull()
            }

        messages.filter(EventName.MANUELL_OPPRETT_SAK_REQUESTED)
            .filter(BehovType.OPPRETT_SAK)
            .first()
            .fromJsonMapOnlyKeys()
            .also {
                it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
            }

        messages.filter(EventName.MANUELL_OPPRETT_SAK_REQUESTED)
            .filter(DataFelt.SAK_ID)
            .first()
            .also {
                val sakId = it.fromJsonMapOnlyDatafelter()[DataFelt.SAK_ID]?.fromJsonToString()

                sakId shouldBe Mock.SAK_ID

                val forespoerselId = it.fromJsonMapOnlyKeys()[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer)

                forespoerselId shouldBe Mock.forespoerselId
            }

        messages.filter(EventName.SAK_OPPRETTET)
            .first()
            .fromJsonMapOnlyDatafelter()
            .also {
                it[DataFelt.SAK_ID]?.fromJsonToString() shouldBe Mock.SAK_ID
            }
    }

    private object Mock {
        const val FNR = "fnr-123"
        const val ORGNR = "orgnr-456"

        const val SAK_ID = "sak_id_123"
        const val OPPGAVE_ID = "oppgave_id_456"

        val forespoerselId = UUID.randomUUID()
    }
}
