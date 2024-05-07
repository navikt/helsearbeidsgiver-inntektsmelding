package no.nav.helsearbeidsgiver.inntektsmelding.aapenimservice

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.date.shouldBeWithin
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.verify
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Arbeidsgiverperiode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Ferietrekk
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Naturalytelse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.RedusertLoennIAgp
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Refusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.RefusjonEndring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaAvsender
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Person
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedis
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.message
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class LagreSelvbestemtImServiceTest : FunSpec({

    val testRapid = TestRapid()
    val mockRedis = MockRedis()

    LagreSelvbestemtImService(testRapid, mockRedis.store)

    beforeEach {
        testRapid.reset()
        clearAllMocks()
        mockRedis.setup()
    }

    test("helt ny inntektsmelding lagres og sak opprettes") {
        val clientId = UUID.randomUUID()
        val transaksjonId = UUID.randomUUID()
        val nyInntektsmelding = Mock.inntektsmelding.copy(aarsakInnsending = AarsakInnsending.Ny)

        mockStatic(::randomUuid) {
            every { randomUuid() } returns transaksjonId

            testRapid.sendJson(
                mockStartMelding(clientId, transaksjonId)
                    .plus(
                        Pair(
                            Key.SKJEMA_INNTEKTSMELDING,
                            Mock.skjema.copy(aarsakInnsending = AarsakInnsending.Ny)
                                .toJson(SkjemaInntektsmelding.serializer())
                        )
                    )
            )
        }

        testRapid.inspektør.size shouldBeExactly 2
        testRapid.message(0).lesBehov() shouldBe BehovType.VIRKSOMHET
        testRapid.message(1).lesBehov() shouldBe BehovType.HENT_PERSONER

        testRapid.sendJson(
            mockSteg1Data(transaksjonId)
        )

        testRapid.inspektør.size shouldBeExactly 3
        testRapid.message(2).also {
            it.lesBehov() shouldBe BehovType.LAGRE_SELVBESTEMT_IM
            it.lesInntektsmelding().shouldBeEqualToIgnoringFields(
                nyInntektsmelding,
                Inntektsmelding::id,
                Inntektsmelding::mottatt
            )
            it.lesInntektsmelding().mottatt.shouldBeWithin(5.seconds.toJavaDuration(), nyInntektsmelding.mottatt)
        }

        testRapid.sendJson(
            mockSteg2Data(transaksjonId, nyInntektsmelding)
        )

        testRapid.inspektør.size shouldBeExactly 4
        testRapid.message(3).lesBehov() shouldBe BehovType.OPPRETT_SELVBESTEMT_SAK

        testRapid.sendJson(
            mockSteg3Data(transaksjonId)
        )

        testRapid.inspektør.size shouldBeExactly 5
        testRapid.message(4).also {
            Key.EVENT_NAME.lesOrNull(EventName.serializer(), it.toMap()) shouldBe EventName.SELVBESTEMT_IM_LAGRET
            Key.UUID.lesOrNull(UuidSerializer, it.toMap()) shouldBe transaksjonId
            it.lesInntektsmelding().shouldBeEqualToIgnoringFields(
                nyInntektsmelding,
                Inntektsmelding::id,
                Inntektsmelding::mottatt
            )
            it.lesInntektsmelding().mottatt.shouldBeWithin(5.seconds.toJavaDuration(), nyInntektsmelding.mottatt)
        }

        verify {
            mockRedis.store.set(
                RedisKey.of(clientId),
                ResultJson(
                    success = nyInntektsmelding.toJson(Inntektsmelding.serializer())
                ).toJsonStr()
            )
        }
    }

    test("endret inntektsmelding lagres uten at sak opprettes") {
        val clientId = UUID.randomUUID()
        val transaksjonId = UUID.randomUUID()
        val endretInntektsmelding = Mock.inntektsmelding.copy(aarsakInnsending = AarsakInnsending.Endring)

        mockStatic(::randomUuid) {
            every { randomUuid() } returns transaksjonId

            testRapid.sendJson(
                mockStartMelding(clientId, transaksjonId)
            )
        }

        testRapid.inspektør.size shouldBeExactly 2
        testRapid.message(0).lesBehov() shouldBe BehovType.VIRKSOMHET
        testRapid.message(1).lesBehov() shouldBe BehovType.HENT_PERSONER

        testRapid.sendJson(
            mockSteg1Data(transaksjonId)
        )

        testRapid.inspektør.size shouldBeExactly 3
        testRapid.message(2).also {
            it.lesBehov() shouldBe BehovType.LAGRE_SELVBESTEMT_IM
            it.lesInntektsmelding().shouldBeEqualToIgnoringFields(
                endretInntektsmelding,
                Inntektsmelding::id,
                Inntektsmelding::mottatt
            )
            it.lesInntektsmelding().mottatt.shouldBeWithin(5.seconds.toJavaDuration(), endretInntektsmelding.mottatt)
        }

        testRapid.sendJson(
            mockSteg2Data(transaksjonId, endretInntektsmelding)
        )

        testRapid.inspektør.size shouldBeExactly 4
        testRapid.message(3).also {
            Key.EVENT_NAME.lesOrNull(EventName.serializer(), it.toMap()) shouldBe EventName.SELVBESTEMT_IM_LAGRET
            Key.UUID.lesOrNull(UuidSerializer, it.toMap()) shouldBe transaksjonId
            it.lesInntektsmelding().shouldBeEqualToIgnoringFields(
                endretInntektsmelding,
                Inntektsmelding::id,
                Inntektsmelding::mottatt
            )
            it.lesInntektsmelding().mottatt.shouldBeWithin(5.seconds.toJavaDuration(), endretInntektsmelding.mottatt)
        }

        verify {
            mockRedis.store.set(
                RedisKey.of(clientId),
                ResultJson(
                    success = endretInntektsmelding.toJson(Inntektsmelding.serializer())
                ).toJsonStr()
            )
        }
    }

    test("duplikat inntektsmelding lagres uten at sluttevent publiseres") {
        val clientId = UUID.randomUUID()
        val transaksjonId = UUID.randomUUID()
        val duplikatInntektsmelding = Mock.inntektsmelding.copy(aarsakInnsending = AarsakInnsending.Endring)

        mockStatic(::randomUuid) {
            every { randomUuid() } returns transaksjonId

            testRapid.sendJson(
                mockStartMelding(clientId, transaksjonId)
            )
        }

        testRapid.sendJson(
            mockSteg1Data(transaksjonId)
        )

        testRapid.inspektør.size shouldBeExactly 3
        testRapid.message(2).also {
            it.lesBehov() shouldBe BehovType.LAGRE_SELVBESTEMT_IM
            it.lesInntektsmelding().shouldBeEqualToIgnoringFields(
                duplikatInntektsmelding,
                Inntektsmelding::id,
                Inntektsmelding::mottatt
            )
            it.lesInntektsmelding().mottatt.shouldBeWithin(5.seconds.toJavaDuration(), duplikatInntektsmelding.mottatt)
        }

        testRapid.sendJson(
            mockSteg2Data(transaksjonId, duplikatInntektsmelding)
                .plus(
                    Key.ER_DUPLIKAT_IM to true.toJson(Boolean.serializer())
                )
        )

        testRapid.inspektør.size shouldBeExactly 3
        Key.EVENT_NAME.lesOrNull(
            EventName.serializer(),
            testRapid.message(2).toMap()
        ) shouldNotBe EventName.SELVBESTEMT_IM_LAGRET

        verify {
            mockRedis.store.set(
                RedisKey.of(clientId),
                ResultJson(
                    success = duplikatInntektsmelding.toJson(Inntektsmelding.serializer())
                ).toJsonStr()
            )
        }
    }

    test("bruk defaultverdier ved håndterbare feil under henting av data") {
        val clientId = UUID.randomUUID()
        val transaksjonId = UUID.randomUUID()

        val virksomhetDefault = "Ukjent virksomhet"
        val inntektsmeldingMedDefaults = Mock.inntektsmelding.let {
            it.copy(
                sykmeldt = it.sykmeldt.copy(
                    navn = ""
                ),
                avsender = it.avsender.copy(
                    orgNavn = virksomhetDefault,
                    navn = ""
                ),
                aarsakInnsending = AarsakInnsending.Endring
            )
        }

        mockStatic(::randomUuid) {
            every { randomUuid() } returns transaksjonId

            testRapid.sendJson(
                mockStartMelding(clientId, transaksjonId)
            )
        }

        testRapid.sendJson(
            Fail(
                feilmelding = "Denne bedriften er skummel.",
                event = EventName.SELVBESTEMT_IM_MOTTATT,
                transaksjonId = transaksjonId,
                forespoerselId = null,
                utloesendeMelding = JsonObject(
                    mapOf(
                        Key.BEHOV.toString() to BehovType.VIRKSOMHET.toJson()
                    )
                )
            ).tilMelding()
        )

        testRapid.sendJson(
            Fail(
                feilmelding = "Denne personen jobber i PST.",
                event = EventName.SELVBESTEMT_IM_MOTTATT,
                transaksjonId = transaksjonId,
                forespoerselId = null,
                utloesendeMelding = JsonObject(
                    mapOf(
                        Key.BEHOV.toString() to BehovType.FULLT_NAVN.toJson()
                    )
                )
            ).tilMelding()
        )

        testRapid.inspektør.size shouldBeExactly 3
        testRapid.message(2).also {
            it.lesBehov() shouldBe BehovType.LAGRE_SELVBESTEMT_IM
            it.lesInntektsmelding().shouldBeEqualToIgnoringFields(
                inntektsmeldingMedDefaults,
                Inntektsmelding::id,
                Inntektsmelding::mottatt
            )
            it.lesInntektsmelding().mottatt.shouldBeWithin(
                5.seconds.toJavaDuration(),
                inntektsmeldingMedDefaults.mottatt
            )
        }

        testRapid.sendJson(
            mockSteg2Data(transaksjonId, inntektsmeldingMedDefaults)
        )

        testRapid.inspektør.size shouldBeExactly 4
        testRapid.message(3).also {
            Key.EVENT_NAME.lesOrNull(EventName.serializer(), it.toMap()) shouldBe EventName.SELVBESTEMT_IM_LAGRET
            Key.UUID.lesOrNull(UuidSerializer, it.toMap()) shouldBe transaksjonId
            it.lesInntektsmelding().shouldBeEqualToIgnoringFields(
                inntektsmeldingMedDefaults,
                Inntektsmelding::id,
                Inntektsmelding::mottatt
            )
            it.lesInntektsmelding().mottatt.shouldBeWithin(
                5.seconds.toJavaDuration(),
                inntektsmeldingMedDefaults.mottatt
            )
        }

        verify {
            mockRedis.store.set(
                RedisKey.of(transaksjonId, Key.VIRKSOMHET),
                virksomhetDefault.toJson().toString()
            )

            mockRedis.store.set(
                RedisKey.of(transaksjonId, Key.PERSONER),
                emptyMap<String, JsonElement>().toJson().toString()
            )

            mockRedis.store.set(
                RedisKey.of(clientId),
                ResultJson(
                    success = inntektsmeldingMedDefaults.toJson(Inntektsmelding.serializer())
                ).toJsonStr()
            )
        }
    }

    test("svar med feilmelding ved uhåndterbare feil") {
        val clientId = UUID.randomUUID()
        val transaksjonId = UUID.randomUUID()

        val feilmelding = "Databasen er full :("
        val endretInntektsmelding = Mock.inntektsmelding.copy(aarsakInnsending = AarsakInnsending.Endring)

        mockStatic(::randomUuid) {
            every { randomUuid() } returns transaksjonId

            testRapid.sendJson(
                mockStartMelding(clientId, transaksjonId)
            )
        }

        testRapid.sendJson(
            mockSteg1Data(transaksjonId)
        )

        testRapid.sendJson(
            Fail(
                feilmelding = feilmelding,
                event = EventName.SELVBESTEMT_IM_MOTTATT,
                transaksjonId = transaksjonId,
                forespoerselId = null,
                utloesendeMelding = JsonObject(
                    mapOf(
                        Key.BEHOV.toString() to BehovType.LAGRE_SELVBESTEMT_IM.toJson()
                    )
                )
            ).tilMelding()
        )

        testRapid.inspektør.size shouldBeExactly 3
        testRapid.message(2).also {
            it.lesBehov() shouldBe BehovType.LAGRE_SELVBESTEMT_IM
            it.lesInntektsmelding().shouldBeEqualToIgnoringFields(
                endretInntektsmelding,
                Inntektsmelding::id,
                Inntektsmelding::mottatt
            )
            it.lesInntektsmelding().mottatt.shouldBeWithin(5.seconds.toJavaDuration(), endretInntektsmelding.mottatt)
        }

        verify {
            mockRedis.store.set(
                RedisKey.of(clientId),
                ResultJson(
                    failure = feilmelding.toJson()
                ).toJsonStr()
            )
        }
    }
})

fun mockStartMelding(clientId: UUID, transaksjonId: UUID): Map<Key, JsonElement> =
    mapOf(
        Key.EVENT_NAME to EventName.SELVBESTEMT_IM_MOTTATT.toJson(),
        Key.CLIENT_ID to clientId.toJson(),
        Key.UUID to transaksjonId.toJson(),
        Key.SELVBESTEMT_ID to Mock.selvbestemtId.toJson(),
        Key.SKJEMA_INNTEKTSMELDING to Mock.skjema.toJson(SkjemaInntektsmelding.serializer()),
        Key.ARBEIDSGIVER_FNR to Mock.avsender.fnr.toJson()
    )

fun mockSteg1Data(transaksjonId: UUID): Map<Key, JsonElement> =
    mapOf(
        Key.EVENT_NAME to EventName.SELVBESTEMT_IM_MOTTATT.toJson(),
        Key.UUID to transaksjonId.toJson(),
        Key.SELVBESTEMT_ID to Mock.selvbestemtId.toJson(),
        Key.DATA to "".toJson(),
        Key.VIRKSOMHET to Mock.ORG_NAVN.toJson(),
        Key.PERSONER to mapOf(
            Mock.sykmeldt.fnr to Mock.sykmeldt,
            Mock.avsender.fnr to Mock.avsender
        ).toJson(personMapSerializer)
    )

fun mockSteg2Data(transaksjonId: UUID, inntektsmelding: Inntektsmelding): Map<Key, JsonElement> =
    mapOf(
        Key.EVENT_NAME to EventName.SELVBESTEMT_IM_MOTTATT.toJson(),
        Key.UUID to transaksjonId.toJson(),
        Key.SELVBESTEMT_ID to Mock.selvbestemtId.toJson(),
        Key.DATA to "".toJson(),
        Key.SELVBESTEMT_INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
        Key.ER_DUPLIKAT_IM to false.toJson(Boolean.serializer())
    )

fun mockSteg3Data(transaksjonId: UUID): Map<Key, JsonElement> =
    mapOf(
        Key.EVENT_NAME to EventName.SELVBESTEMT_IM_MOTTATT.toJson(),
        Key.UUID to transaksjonId.toJson(),
        Key.SELVBESTEMT_ID to Mock.selvbestemtId.toJson(),
        Key.DATA to "".toJson(),
        Key.SAK_ID to "folkelig-lurendreier-sak-id".toJson()
    )

private fun JsonElement.lesBehov(): BehovType? =
    Key.BEHOV.lesOrNull(BehovType.serializer(), this.toMap())

private fun JsonElement.lesInntektsmelding(): Inntektsmelding =
    Key.SELVBESTEMT_INNTEKTSMELDING.lesOrNull(Inntektsmelding.serializer(), this.toMap()).shouldNotBeNull()

private object Mock {
    const val ORG_NAVN = "Keiser Augustus' Ponniutleie"
    val selvbestemtId: UUID = UUID.randomUUID()
    val sykmeldt = Fnr.genererGyldig().verdi.let {
        Person(
            fnr = it,
            navn = "Ponnius Pilatus",
            foedselsdato = Person.foedselsdato(it)
        )
    }
    val avsender = Fnr.genererGyldig().verdi.let {
        Person(
            fnr = it,
            navn = "King Kong Keiser",
            foedselsdato = Person.foedselsdato(it)
        )
    }

    val skjema = SkjemaInntektsmelding(
        sykmeldtFnr = sykmeldt.fnr,
        avsender = SkjemaAvsender(
            orgnr = Orgnr.genererGyldig().verdi,
            tlf = "43431234"
        ),
        sykmeldingsperioder = listOf(
            14.mars til 14.april
        ),
        agp = Arbeidsgiverperiode(
            perioder = listOf(
                13.mars til 28.mars
            ),
            egenmeldinger = listOf(
                13.mars til 13.mars
            ),
            redusertLoennIAgp = RedusertLoennIAgp(
                beloep = 606.0,
                begrunnelse = RedusertLoennIAgp.Begrunnelse.ManglerOpptjening
            )
        ),
        inntekt = Inntekt(
            beloep = 32100.0,
            inntektsdato = 13.mars,
            naturalytelser = listOf(
                Naturalytelse(
                    naturalytelse = Naturalytelse.Kode.AKSJERGRUNNFONDSBEVISTILUNDERKURS,
                    verdiBeloep = 4000.5,
                    sluttdato = 20.mars
                ),
                Naturalytelse(
                    naturalytelse = Naturalytelse.Kode.LOSJI,
                    verdiBeloep = 700.0,
                    sluttdato = 22.mars
                )
            ),
            endringAarsak = Ferietrekk
        ),
        refusjon = Refusjon(
            beloepPerMaaned = 12000.0,
            endringer = listOf(
                RefusjonEndring(
                    beloep = 11000.0,
                    startdato = 15.mars
                ),
                RefusjonEndring(
                    beloep = 1000.0,
                    startdato = 20.mars
                )
            ),
            sluttdato = 30.mars
        ),
        aarsakInnsending = AarsakInnsending.Endring
    )

    val inntektsmelding = tilInntektsmelding(
        selvbestemtId = selvbestemtId,
        skjema = skjema,
        orgNavn = ORG_NAVN,
        sykmeldt = sykmeldt,
        avsender = avsender
    )
}
