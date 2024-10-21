package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.felles.domene.PersonDato
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.orgMapSerializer
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
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
class ForespoerselMottattIT : EndToEndTest() {
    @Test
    fun `Oppretter sak og oppgave ved mottatt forespørsel`() {
        coEvery {
            arbeidsgiverNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any())
        } returns Mock.sakId

        coEvery {
            arbeidsgiverNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Mock.oppgaveId

        publish(
            Pri.Key.NOTIS to Pri.NotisType.FORESPØRSEL_MOTTATT.toJson(Pri.NotisType.serializer()),
            Pri.Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
            Pri.Key.ORGNR to Mock.orgnr.toJson(),
            Pri.Key.FNR to Mock.fnr.toJson(),
        )

        val messagesFilteredForespoerselMottatt = messages.filter(EventName.FORESPOERSEL_MOTTATT)

        messagesFilteredForespoerselMottatt
            .firstAsMap()
            .also {
                Key.UUID.lesOrNull(UuidSerializer, it).shouldNotBeNull()

                Key.EVENT_NAME.lesOrNull(EventName.serializer(), it) shouldBe EventName.FORESPOERSEL_MOTTATT

                val data = it[Key.DATA].shouldNotBeNull().toMap()
                Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, data) shouldBe Mock.forespoerselId
                Key.ORGNRUNDERENHET.lesOrNull(Orgnr.serializer(), data) shouldBe Mock.orgnr
                Key.FNR.lesOrNull(Fnr.serializer(), data) shouldBe Mock.fnr
            }

        messagesFilteredForespoerselMottatt
            .filter(BehovType.LAGRE_FORESPOERSEL)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
                data[Key.ORGNRUNDERENHET]?.fromJson(Orgnr.serializer()) shouldBe Mock.orgnr
            }

        messagesFilteredForespoerselMottatt
            .filter(BehovType.HENT_VIRKSOMHET_NAVN)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.ORGNR_UNDERENHETER]?.fromJson(Orgnr.serializer().set()) shouldBe setOf(Mock.orgnr)
            }

        messagesFilteredForespoerselMottatt
            .filter(BehovType.HENT_PERSONER)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.FNR_LISTE]?.fromJson(Fnr.serializer().set()) shouldBe setOf(Mock.fnr)
            }

        messagesFilteredForespoerselMottatt
            .filter(Key.VIRKSOMHETER)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.VIRKSOMHETER]
                    ?.fromJson(orgMapSerializer)
                    .shouldNotBeNull()
            }

        messagesFilteredForespoerselMottatt
            .filter(Key.PERSONER)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.PERSONER]
                    ?.fromJson(personMapSerializer)
                    .shouldNotBeNull()
            }

        messagesFilteredForespoerselMottatt
            .filter(BehovType.OPPRETT_SAK)
            .firstAsMap()
            .also {
                it[Key.UUID]?.fromJson(UuidSerializer).shouldNotBeNull()
                it[Key.ARBEIDSTAKER_INFORMASJON]?.fromJson(PersonDato.serializer()).shouldNotBeNull()
                it[Key.PERSONER]?.fromJson(Person.serializer()).shouldNotBeNull()

                it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
                it[Key.ORGNRUNDERENHET]?.fromJson(Orgnr.serializer()) shouldBe Mock.orgnr
            }

        messagesFilteredForespoerselMottatt
            .filter(BehovType.OPPRETT_OPPGAVE)
            .firstAsMap()
            .also {
                it[Key.UUID]?.fromJson(UuidSerializer).shouldNotBeNull()
                it[Key.VIRKSOMHET]?.fromJson(String.serializer()).shouldNotBeNull()

                it[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
                it[Key.ORGNRUNDERENHET]?.fromJson(Orgnr.serializer()) shouldBe Mock.orgnr
            }

        messages
            .filter(EventName.SAK_OPPRETTET)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.SAK_ID]?.fromJson(String.serializer()) shouldBe Mock.sakId
            }

        messages
            .filter(EventName.OPPGAVE_OPPRETTET)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.OPPGAVE_ID]?.fromJson(String.serializer()) shouldBe Mock.oppgaveId
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
            Key.SAK_ID to Mock.sakId.toJson(),
        )

        coVerify(exactly = 1) { arbeidsgiverNotifikasjonKlient.hardDeleteSak(Mock.sakId) }
        messages.all().size shouldBe 1
    }

    private object Mock {
        val forespoerselId: UUID = UUID.randomUUID()
        val orgnr = Orgnr.genererGyldig()
        val fnr = Fnr.genererGyldig()
        val sakId = UUID.randomUUID().toString()
        val oppgaveId = UUID.randomUUID().toString()
    }
}
