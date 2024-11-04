package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.ForespoerselFraBro
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.orgMapSerializer
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.pritopic.Pri
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtData
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.mars
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
            agNotifikasjonKlient.opprettNySak(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Mock.sakId

        coEvery {
            agNotifikasjonKlient.opprettNyOppgave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Mock.oppgaveId

        publish(
            Pri.Key.NOTIS to Pri.NotisType.FORESPØRSEL_MOTTATT.toJson(Pri.NotisType.serializer()),
            Pri.Key.FORESPOERSEL_ID to Mock.forespoerselId.toJson(),
            Pri.Key.ORGNR to Mock.orgnr.toJson(),
            Pri.Key.FNR to Mock.fnr.toJson(),
            Pri.Key.SKAL_HA_PAAMINNELSE to Mock.skalHaPaaminnelse.toJson(Boolean.serializer()),
            Pri.Key.FORESPOERSEL to
                ForespoerselFraBro(
                    orgnr = Mock.orgnr,
                    fnr = Mock.fnr,
                    forespoerselId = Mock.forespoerselId,
                    vedtaksperiodeId = UUID.randomUUID(),
                    sykmeldingsperioder = listOf(23.januar til 15.mars),
                    egenmeldingsperioder = emptyList(),
                    bestemmendeFravaersdager = emptyMap(),
                    forespurtData = mockForespurtData(),
                    erBesvart = false,
                ).toJson(ForespoerselFraBro.serializer()),
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

        messages
            .filter(EventName.SAK_OG_OPPGAVE_OPPRETT_REQUESTED)
            .firstAsMap()
            .also {
                it[Key.UUID]?.fromJson(UuidSerializer).shouldNotBeNull()

                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.SYKMELDT]?.fromJson(Person.serializer()).shouldNotBeNull()
                data[Key.VIRKSOMHET]?.fromJson(String.serializer()).shouldNotBeNull()

                data[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
                data[Key.ORGNRUNDERENHET]?.fromJson(Orgnr.serializer()) shouldBe Mock.orgnr
                data[Key.SKAL_HA_PAAMINNELSE]?.fromJson(Boolean.serializer()) shouldBe Mock.skalHaPaaminnelse
            }

        messages
            .filter(EventName.SAK_OG_OPPGAVE_OPPRETTET)
            .firstAsMap()
            .also {
                val data = it[Key.DATA].shouldNotBeNull().toMap()
                data[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer) shouldBe Mock.forespoerselId
                data[Key.SAK_ID]?.fromJson(String.serializer()) shouldBe Mock.sakId
                data[Key.OPPGAVE_ID]?.fromJson(String.serializer()) shouldBe Mock.oppgaveId
            }
    }

    private object Mock {
        val forespoerselId: UUID = UUID.randomUUID()
        val orgnr = Orgnr.genererGyldig()
        val fnr = Fnr.genererGyldig()
        val sakId = UUID.randomUUID().toString()
        val oppgaveId = UUID.randomUUID().toString()
        val skalHaPaaminnelse = false
    }
}
