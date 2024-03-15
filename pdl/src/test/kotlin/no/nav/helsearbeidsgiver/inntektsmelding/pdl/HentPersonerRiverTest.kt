package no.nav.helsearbeidsgiver.inntektsmelding.pdl

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import io.prometheus.client.CollectorRegistry
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Person
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.pdl.PdlClient
import no.nav.helsearbeidsgiver.pdl.domene.FullPerson
import no.nav.helsearbeidsgiver.pdl.domene.PersonNavn
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.juni
import java.util.UUID

class HentPersonerRiverTest : FunSpec({

    val testRapid = TestRapid()
    val mockPdlClient = mockk<PdlClient>()

    HentPersonerRiver(mockPdlClient).connect(testRapid)

    beforeEach {
        testRapid.reset()
        clearAllMocks()
        CollectorRegistry.defaultRegistry.clear()
    }

    test("finner én person") {
        val transaksjonId = UUID.randomUUID()
        val olaFnr = "123"

        coEvery { mockPdlClient.personBolk(any()) } returns listOf(mockFullPerson("Ola", olaFnr))

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.BEHOV to BehovType.HENT_PERSONER.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FNR_LISTE to listOf(olaFnr).toJson(String.serializer())
        )

        testRapid.inspektør.size shouldBeExactly 1

        val publisert = testRapid.firstMessage().toMap()

        val personMap = Key.PERSONER.lesOrNull(personMapSerializer, publisert)
            .shouldNotBeNull()

        personMap shouldHaveSize 1

        personMap[olaFnr]
            .shouldNotBeNull()
            .let {
                it.fnr shouldBe olaFnr
                it.navn shouldBe "Ola Normann"
            }

        Key.EVENT_NAME.les(EventName.serializer(), publisert) shouldBe EventName.INSENDING_STARTED
        Key.UUID.les(UuidSerializer, publisert) shouldBe transaksjonId
        Key.DATA.les(String.serializer(), publisert) shouldBe ""

        publisert[Key.BEHOV].shouldBeNull()
        publisert[Key.FAIL].shouldBeNull()
    }

    test("finner flere personer") {
        val transaksjonId = UUID.randomUUID()
        val olaFnr = "123456"
        val kariFnr = "654321"

        coEvery {
            mockPdlClient.personBolk(any())
        } returns listOf(
            mockFullPerson("Ola", olaFnr),
            mockFullPerson("Kari", kariFnr)
        )

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
            Key.BEHOV to BehovType.HENT_PERSONER.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FNR_LISTE to listOf(olaFnr, kariFnr).toJson(String.serializer())
        )

        testRapid.inspektør.size shouldBeExactly 1

        val publisert = testRapid.firstMessage().toMap()

        val personMap = Key.PERSONER.lesOrNull(personMapSerializer, publisert)
            .shouldNotBeNull()

        personMap shouldHaveSize 2

        personMap[olaFnr]
            .shouldNotBeNull()
            .let {
                it.fnr shouldBe olaFnr
                it.navn shouldBe "Ola Normann"
            }

        personMap[kariFnr]
            .shouldNotBeNull()
            .let {
                it.fnr shouldBe kariFnr
                it.navn shouldBe "Kari Normann"
            }

        Key.EVENT_NAME.les(EventName.serializer(), publisert) shouldBe EventName.TRENGER_REQUESTED
        Key.UUID.les(UuidSerializer, publisert) shouldBe transaksjonId
        Key.DATA.les(String.serializer(), publisert) shouldBe ""

        publisert[Key.BEHOV].shouldBeNull()
        publisert[Key.FAIL].shouldBeNull()
    }

    test("returnerer kun personer som blir funnet") {
        val transaksjonId = UUID.randomUUID()
        val olaFnr = "123456"
        val kariFnr = "654321"

        coEvery {
            mockPdlClient.personBolk(any())
        } returns listOf(
            mockFullPerson("Kari", kariFnr)
        )

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
            Key.BEHOV to BehovType.HENT_PERSONER.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FNR_LISTE to listOf(olaFnr, kariFnr).toJson(String.serializer())
        )

        testRapid.inspektør.size shouldBeExactly 1

        val publisert = testRapid.firstMessage().toMap()

        val personMap = Key.PERSONER.lesOrNull(personMapSerializer, publisert)
            .shouldNotBeNull()

        personMap shouldHaveSize 1

        personMap[olaFnr].shouldBeNull()

        personMap[kariFnr]
            .shouldNotBeNull()
            .let {
                it.fnr shouldBe kariFnr
                it.navn shouldBe "Kari Normann"
            }

        Key.EVENT_NAME.les(EventName.serializer(), publisert) shouldBe EventName.TRENGER_REQUESTED
        Key.UUID.les(UuidSerializer, publisert) shouldBe transaksjonId
        Key.DATA.les(String.serializer(), publisert) shouldBe ""

        publisert[Key.BEHOV].shouldBeNull()
        publisert[Key.FAIL].shouldBeNull()
    }

    test("sender med forespoerselId dersom det finnes i utløsende melding") {
        val transaksjonId = UUID.randomUUID()
        val forespoerselId = UUID.randomUUID()
        val olaFnr = "123456"

        coEvery {
            mockPdlClient.personBolk(any())
        } returns listOf(
            mockFullPerson("Ola", olaFnr)
        )

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
            Key.BEHOV to BehovType.HENT_PERSONER.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            Key.FNR_LISTE to listOf(olaFnr).toJson(String.serializer())
        )

        testRapid.inspektør.size shouldBeExactly 1

        val publisert = testRapid.firstMessage().toMap()

        Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, publisert)
            .shouldNotBeNull()
            .shouldBe(forespoerselId)

        Key.SELVBESTEMT_ID.lesOrNull(UuidSerializer, publisert).shouldBeNull()

        Key.PERSONER.lesOrNull(personMapSerializer, publisert)
            .shouldNotBeNull()
            .shouldHaveSize(1)

        Key.EVENT_NAME.les(EventName.serializer(), publisert) shouldBe EventName.TRENGER_REQUESTED
        Key.UUID.les(UuidSerializer, publisert) shouldBe transaksjonId
        Key.DATA.les(String.serializer(), publisert) shouldBe ""

        publisert[Key.BEHOV].shouldBeNull()
        publisert[Key.FAIL].shouldBeNull()
    }

    test("sender med selvbestemtId dersom det finnes i utløsende melding") {
        val transaksjonId = UUID.randomUUID()
        val selvbestemtId = UUID.randomUUID()
        val olaFnr = "123456"

        coEvery {
            mockPdlClient.personBolk(any())
        } returns listOf(
            mockFullPerson("Ola", olaFnr)
        )

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
            Key.BEHOV to BehovType.HENT_PERSONER.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.SELVBESTEMT_ID to selvbestemtId.toJson(),
            Key.FNR_LISTE to listOf(olaFnr).toJson(String.serializer())
        )

        testRapid.inspektør.size shouldBeExactly 1

        val publisert = testRapid.firstMessage().toMap()

        Key.SELVBESTEMT_ID.lesOrNull(UuidSerializer, publisert)
            .shouldNotBeNull()
            .shouldBe(selvbestemtId)

        Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, publisert).shouldBeNull()

        Key.PERSONER.lesOrNull(personMapSerializer, publisert)
            .shouldNotBeNull()
            .shouldHaveSize(1)

        Key.EVENT_NAME.les(EventName.serializer(), publisert) shouldBe EventName.TRENGER_REQUESTED
        Key.UUID.les(UuidSerializer, publisert) shouldBe transaksjonId
        Key.DATA.les(String.serializer(), publisert) shouldBe ""

        publisert[Key.BEHOV].shouldBeNull()
        publisert[Key.FAIL].shouldBeNull()
    }

    test("håndterer ukjente feil") {
        val transaksjonId = UUID.randomUUID()
        val forespoerselId = UUID.randomUUID()
        val selvbestemtId = UUID.randomUUID()

        coEvery { mockPdlClient.personBolk(any()) } throws IllegalArgumentException("Finner bare brødristere!")

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
            Key.BEHOV to BehovType.HENT_PERSONER.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            Key.SELVBESTEMT_ID to selvbestemtId.toJson(),
            Key.FNR_LISTE to listOf("666").toJson(String.serializer())
        )

        testRapid.inspektør.size shouldBeExactly 1

        val publisert = testRapid.firstMessage().toMap()

        publisert[Key.PERSONER].shouldBeNull()

        val fail = Key.FAIL.les(Fail.serializer(), publisert)

        fail.feilmelding shouldBe "Klarte ikke hente personer fra PDL."
        fail.event shouldBe EventName.TRENGER_REQUESTED
        fail.transaksjonId shouldBe transaksjonId
        fail.forespoerselId shouldBe forespoerselId
        fail.utloesendeMelding.toMap()[Key.BEHOV]?.fromJson(BehovType.serializer()) shouldBe BehovType.HENT_PERSONER

        Key.EVENT_NAME.les(EventName.serializer(), publisert) shouldBe EventName.TRENGER_REQUESTED
        Key.UUID.les(UuidSerializer, publisert) shouldBe transaksjonId
        Key.FORESPOERSEL_ID.les(UuidSerializer, publisert) shouldBe forespoerselId
        Key.SELVBESTEMT_ID.les(UuidSerializer, publisert) shouldBe selvbestemtId

        publisert[Key.BEHOV].shouldBeNull()
        publisert[Key.DATA].shouldBeNull()
    }
})

private val personMapSerializer =
    MapSerializer(
        String.serializer(),
        Person.serializer()
    )

private fun mockFullPerson(fornavn: String, ident: String): FullPerson =
    FullPerson(
        navn = PersonNavn(
            fornavn = fornavn,
            mellomnavn = null,
            etternavn = "Normann"
        ),
        foedselsdato = 13.juni(1956),
        ident = ident
    )
