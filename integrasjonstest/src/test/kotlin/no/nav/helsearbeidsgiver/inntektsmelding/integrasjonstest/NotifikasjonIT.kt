package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.fromJsonToString
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotifikasjonIT : EndToEndTest() {
    @Test
    fun `Oppretter og lagrer sak etter at forespørselen er mottatt`() {
        coEvery {
            arbeidsgiverNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any())
        } returns Mock.SAK_ID

        publish(
            Key.EVENT_NAME to EventName.SAK_OPPRETT_REQUESTED.toJson(),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
                    Key.ORGNRUNDERENHET to Mock.orgnr.toJson(),
                    Key.IDENTITETSNUMMER to Mock.fnr.toJson(),
                ).toJson(),
        )

        messages
            .filter(EventName.SAK_OPPRETT_REQUESTED)
            .filter(BehovType.HENT_PERSONER)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
                data[Key.FNR_LISTE]?.fromJson(Fnr.serializer().set()) shouldBe setOf(Mock.fnr)
            }

        messages
            .filter(EventName.SAK_OPPRETT_REQUESTED)
            .filter(Key.PERSONER)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.PERSONER]
                    ?.fromJson(personMapSerializer)
                    .shouldNotBeNull()
            }

        messages
            .filter(EventName.SAK_OPPRETT_REQUESTED)
            .filter(BehovType.OPPRETT_SAK)
            .firstAsMap()
            .also {
                it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
            }

        messages
            .filter(EventName.SAK_OPPRETT_REQUESTED)
            .filter(Key.SAK_ID, nestedData = false)
            .firstAsMap()
            .also {
                val sakId = it[Key.SAK_ID]?.fromJsonToString()

                sakId shouldBe Mock.SAK_ID

                val forespoerselId = it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer)

                forespoerselId shouldBe Mock.forespoerselId
            }

        messages
            .filter(EventName.SAK_OPPRETTET)
            .firstAsMap()
            .also {
                it[Key.SAK_ID]?.fromJsonToString() shouldBe Mock.SAK_ID
            }
    }

    @Test
    fun `Oppretter og lagrer oppgave etter at forespørselen er mottatt`() {
        coEvery {
            arbeidsgiverNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Mock.OPPGAVE_ID

        publish(
            Key.EVENT_NAME to EventName.OPPGAVE_OPPRETT_REQUESTED.toJson(),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
                    Key.ORGNRUNDERENHET to Mock.orgnr.toJson(),
                ).toJson(),
        )

        messages
            .filter(EventName.OPPGAVE_OPPRETT_REQUESTED)
            .filter(BehovType.OPPRETT_OPPGAVE)
            .all()
            .also { it.size shouldBe 1 }
            .first()
            .toMap()
            .also {
                it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
                it[Key.UUID]?.fromJson(UuidSerializer).shouldNotBeNull()

                val orgnr = it[Key.ORGNRUNDERENHET]?.fromJson(Orgnr.serializer())

                orgnr shouldBe Mock.orgnr
            }

        messages
            .filter(EventName.OPPGAVE_OPPRETT_REQUESTED)
            .filter(BehovType.PERSISTER_OPPGAVE_ID)
            .firstAsMap()
            .also {
                val oppgaveId = it[Key.OPPGAVE_ID]?.fromJsonToString()

                oppgaveId shouldBe Mock.OPPGAVE_ID

                it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
            }

        messages
            .filter(EventName.OPPGAVE_LAGRET)
            .firstAsMap()
            .also {
                val oppgaveId = it[Key.OPPGAVE_ID]?.fromJsonToString()

                oppgaveId shouldBe Mock.OPPGAVE_ID
            }
    }

    @Test
    fun `Slett sak loeser test`() {
        coEvery {
            arbeidsgiverNotifikasjonKlient.hardDeleteSak(any())
        } returns Unit

        publish(
            Key.EVENT_NAME to EventName.MANUELL_SLETT_SAK_REQUESTED.toJson(),
            Key.BEHOV to BehovType.SLETT_SAK.toJson(),
            Key.SAK_ID to Mock.SAK_ID.toJson(),
        )

        coVerify(exactly = 1) { arbeidsgiverNotifikasjonKlient.hardDeleteSak(Mock.SAK_ID) }
        messages.all().size shouldBe 1
    }

    private object Mock {
        const val SAK_ID = "sak_id_123"
        const val OPPGAVE_ID = "oppgave_id_456"

        val forespoerselId: UUID = UUID.randomUUID()
        val fnr = Fnr.genererGyldig()
        val orgnr = Orgnr.genererGyldig()
    }
}
