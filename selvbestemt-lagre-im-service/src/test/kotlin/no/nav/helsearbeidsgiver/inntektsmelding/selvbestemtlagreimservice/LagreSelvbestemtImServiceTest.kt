package no.nav.helsearbeidsgiver.inntektsmelding.selvbestemtlagreimservice

import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
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
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmeldingSelvbestemt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.Ansettelsesperiode
import no.nav.helsearbeidsgiver.felles.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.Arbeidsgiver
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PeriodeNullable
import no.nav.helsearbeidsgiver.felles.Person
import no.nav.helsearbeidsgiver.felles.ResultJson
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateful
import no.nav.helsearbeidsgiver.felles.test.json.lesBehov
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedis
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.message
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

class LagreSelvbestemtImServiceTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockRedis = MockRedis(RedisPrefix.LagreSelvbestemtIm)

        ServiceRiverStateful(
            LagreSelvbestemtImService(testRapid, mockRedis.store),
        ).connect(testRapid)

        beforeEach {
            testRapid.reset()
            clearAllMocks()
            mockRedis.setup()
        }

        test("helt ny inntektsmelding lagres og sak opprettes") {
            val transaksjonId = UUID.randomUUID()
            val nyInntektsmelding = Mock.inntektsmelding.copy(aarsakInnsending = AarsakInnsending.Ny)

            testRapid.sendJson(
                Mock
                    .steg0(transaksjonId)
                    .plus(
                        Pair(
                            Key.SKJEMA_INNTEKTSMELDING,
                            Mock.skjema
                                .copy(selvbestemtId = null)
                                .toJson(SkjemaInntektsmeldingSelvbestemt.serializer()),
                        ),
                    ),
            )

            testRapid.inspektør.size shouldBeExactly 3
            testRapid.message(0).lesBehov() shouldBe BehovType.HENT_VIRKSOMHET_NAVN
            testRapid.message(1).lesBehov() shouldBe BehovType.HENT_PERSONER
            testRapid.message(2).lesBehov() shouldBe BehovType.HENT_ARBEIDSFORHOLD

            mockStatic(OffsetDateTime::class) {
                every { OffsetDateTime.now() } returns nyInntektsmelding.mottatt

                testRapid.sendJson(
                    Mock
                        .steg1(transaksjonId)
                        .minus(Key.SELVBESTEMT_ID),
                )
            }

            testRapid.inspektør.size shouldBeExactly 4
            testRapid.message(3).also {
                it.lesBehov() shouldBe BehovType.LAGRE_SELVBESTEMT_IM
                it.lesInntektsmelding().shouldBeEqualToIgnoringFields(nyInntektsmelding, Inntektsmelding::id, Inntektsmelding::type)

                val type = it.lesInntektsmelding().type
                when (type) {
                    is Inntektsmelding.Type.Forespurt -> fail("Feil type: $type")
                    is Inntektsmelding.Type.Selvbestemt -> type.id shouldNotBe nyInntektsmelding.type.id
                }
            }

            testRapid.sendJson(
                Mock
                    .steg2(transaksjonId, nyInntektsmelding)
                    .minus(Key.SELVBESTEMT_ID),
            )

            testRapid.inspektør.size shouldBeExactly 5
            testRapid.message(4).lesBehov() shouldBe BehovType.OPPRETT_SELVBESTEMT_SAK

            testRapid.sendJson(
                Mock
                    .steg3(transaksjonId)
                    .minus(Key.SELVBESTEMT_ID),
            )

            testRapid.inspektør.size shouldBeExactly 6
            testRapid.message(5).also {
                Key.EVENT_NAME.lesOrNull(EventName.serializer(), it.toMap()) shouldBe EventName.SELVBESTEMT_IM_LAGRET
                Key.UUID.lesOrNull(UuidSerializer, it.toMap()) shouldBe transaksjonId
                it.lesInntektsmelding().shouldBeEqualToIgnoringFields(nyInntektsmelding, Inntektsmelding::id)
            }

            verify {
                mockRedis.store.set(
                    RedisKey.of(transaksjonId),
                    ResultJson(
                        success = nyInntektsmelding.type.id.toJson(),
                    ).toJson(ResultJson.serializer()),
                )
            }
        }

        test("endret inntektsmelding lagres uten at sak opprettes") {
            val transaksjonId = UUID.randomUUID()
            val endretInntektsmelding = Mock.inntektsmelding.copy(aarsakInnsending = AarsakInnsending.Endring)

            testRapid.sendJson(
                Mock.steg0(transaksjonId),
            )

            testRapid.inspektør.size shouldBeExactly 3
            testRapid.message(0).lesBehov() shouldBe BehovType.HENT_VIRKSOMHET_NAVN
            testRapid.message(1).lesBehov() shouldBe BehovType.HENT_PERSONER
            testRapid.message(2).lesBehov() shouldBe BehovType.HENT_ARBEIDSFORHOLD

            mockStatic(OffsetDateTime::class) {
                every { OffsetDateTime.now() } returns endretInntektsmelding.mottatt

                testRapid.sendJson(
                    Mock.steg1(transaksjonId),
                )
            }

            testRapid.inspektør.size shouldBeExactly 4
            testRapid.message(3).also {
                it.lesBehov() shouldBe BehovType.LAGRE_SELVBESTEMT_IM
                it.lesInntektsmelding().shouldBeEqualToIgnoringFields(endretInntektsmelding, Inntektsmelding::id)
            }

            testRapid.sendJson(
                Mock.steg2(transaksjonId, endretInntektsmelding),
            )

            testRapid.inspektør.size shouldBeExactly 5
            testRapid.message(4).also {
                Key.EVENT_NAME.lesOrNull(EventName.serializer(), it.toMap()) shouldBe EventName.SELVBESTEMT_IM_LAGRET
                Key.UUID.lesOrNull(UuidSerializer, it.toMap()) shouldBe transaksjonId
                it.lesInntektsmelding().shouldBeEqualToIgnoringFields(endretInntektsmelding, Inntektsmelding::id)
            }

            verify {
                mockRedis.store.set(
                    RedisKey.of(transaksjonId),
                    ResultJson(
                        success = endretInntektsmelding.type.id.toJson(),
                    ).toJson(ResultJson.serializer()),
                )
            }
        }

        test("duplikat inntektsmelding lagres uten at sluttevent publiseres") {
            val transaksjonId = UUID.randomUUID()
            val duplikatInntektsmelding = Mock.inntektsmelding.copy(aarsakInnsending = AarsakInnsending.Endring)

            testRapid.sendJson(
                Mock.steg0(transaksjonId),
            )

            mockStatic(OffsetDateTime::class) {
                every { OffsetDateTime.now() } returns duplikatInntektsmelding.mottatt

                testRapid.sendJson(
                    Mock.steg1(transaksjonId),
                )
            }

            testRapid.inspektør.size shouldBeExactly 4
            testRapid.message(3).also {
                it.lesBehov() shouldBe BehovType.LAGRE_SELVBESTEMT_IM
                it.lesInntektsmelding().shouldBeEqualToIgnoringFields(duplikatInntektsmelding, Inntektsmelding::id)
            }

            testRapid.sendJson(
                Mock
                    .steg2(transaksjonId, duplikatInntektsmelding)
                    .plus(
                        Key.ER_DUPLIKAT_IM to true.toJson(Boolean.serializer()),
                    ),
            )

            testRapid.inspektør.size shouldBeExactly 4
            Key.EVENT_NAME.lesOrNull(
                EventName.serializer(),
                testRapid.message(3).toMap(),
            ) shouldNotBe EventName.SELVBESTEMT_IM_LAGRET

            verify {
                mockRedis.store.set(
                    RedisKey.of(transaksjonId),
                    ResultJson(
                        success = duplikatInntektsmelding.type.id.toJson(),
                    ).toJson(ResultJson.serializer()),
                )
            }
        }

        test("bruk defaultverdier ved håndterbare feil under henting av data") {
            val transaksjonId = UUID.randomUUID()

            val virksomhetDefault = "Ukjent virksomhet"
            val inntektsmeldingMedDefaults =
                Mock.inntektsmelding.let {
                    it.copy(
                        sykmeldt =
                            it.sykmeldt.copy(
                                navn = "",
                            ),
                        avsender =
                            it.avsender.copy(
                                orgNavn = virksomhetDefault,
                                navn = "",
                            ),
                        aarsakInnsending = AarsakInnsending.Endring,
                    )
                }

            testRapid.sendJson(
                Mock.steg0(transaksjonId),
            )
            testRapid.sendJson(
                Mock.steg1(transaksjonId).minus(Key.VIRKSOMHET).minus(Key.PERSONER),
            )

            testRapid.sendJson(
                Fail(
                    feilmelding = "Denne bedriften er skummel.",
                    event = EventName.SELVBESTEMT_IM_MOTTATT,
                    transaksjonId = transaksjonId,
                    forespoerselId = null,
                    utloesendeMelding =
                        JsonObject(
                            mapOf(
                                Key.BEHOV.toString() to BehovType.HENT_VIRKSOMHET_NAVN.toJson(),
                            ),
                        ),
                ).tilMelding(),
            )

            mockStatic(OffsetDateTime::class) {
                every { OffsetDateTime.now() } returns inntektsmeldingMedDefaults.mottatt

                testRapid.sendJson(
                    Fail(
                        feilmelding = "Denne personen jobber i PST.",
                        event = EventName.SELVBESTEMT_IM_MOTTATT,
                        transaksjonId = transaksjonId,
                        forespoerselId = null,
                        utloesendeMelding =
                            JsonObject(
                                mapOf(
                                    Key.BEHOV.toString() to BehovType.HENT_PERSONER.toJson(),
                                ),
                            ),
                    ).tilMelding(),
                )
            }

            testRapid.inspektør.size shouldBeExactly 4
            testRapid.message(3).also {
                it.lesBehov() shouldBe BehovType.LAGRE_SELVBESTEMT_IM
                it.lesInntektsmelding().shouldBeEqualToIgnoringFields(inntektsmeldingMedDefaults, Inntektsmelding::id)
            }

            testRapid.sendJson(
                Mock.steg2(transaksjonId, inntektsmeldingMedDefaults),
            )

            testRapid.inspektør.size shouldBeExactly 5
            testRapid.message(4).also {
                Key.EVENT_NAME.lesOrNull(EventName.serializer(), it.toMap()) shouldBe EventName.SELVBESTEMT_IM_LAGRET
                Key.UUID.lesOrNull(UuidSerializer, it.toMap()) shouldBe transaksjonId
                it.lesInntektsmelding().shouldBeEqualToIgnoringFields(inntektsmeldingMedDefaults, Inntektsmelding::id)
            }

            verify {
                mockRedis.store.set(
                    RedisKey.of(transaksjonId, Key.VIRKSOMHET),
                    virksomhetDefault.toJson(),
                )

                mockRedis.store.set(
                    RedisKey.of(transaksjonId, Key.PERSONER),
                    emptyMap<String, JsonElement>().toJson(),
                )

                mockRedis.store.set(
                    RedisKey.of(transaksjonId),
                    ResultJson(
                        success = inntektsmeldingMedDefaults.type.id.toJson(),
                    ).toJson(ResultJson.serializer()),
                )
            }
        }

        test("svar med feilmelding ved uhåndterbare feil") {
            val transaksjonId = UUID.randomUUID()

            val feilmelding = "Databasen er full :("
            val endretInntektsmelding = Mock.inntektsmelding.copy(aarsakInnsending = AarsakInnsending.Endring)

            testRapid.sendJson(
                Mock.steg0(transaksjonId),
            )

            mockStatic(OffsetDateTime::class) {
                every { OffsetDateTime.now() } returns endretInntektsmelding.mottatt

                testRapid.sendJson(
                    Mock.steg1(transaksjonId),
                )
            }

            testRapid.sendJson(
                Fail(
                    feilmelding = feilmelding,
                    event = EventName.SELVBESTEMT_IM_MOTTATT,
                    transaksjonId = transaksjonId,
                    forespoerselId = null,
                    utloesendeMelding =
                        JsonObject(
                            mapOf(
                                Key.BEHOV.toString() to BehovType.LAGRE_SELVBESTEMT_IM.toJson(),
                            ),
                        ),
                ).tilMelding(),
            )

            testRapid.inspektør.size shouldBeExactly 4
            testRapid.message(3).also {
                it.lesBehov() shouldBe BehovType.LAGRE_SELVBESTEMT_IM
                it.lesInntektsmelding().shouldBeEqualToIgnoringFields(endretInntektsmelding, Inntektsmelding::id)
            }

            verify {
                mockRedis.store.set(
                    RedisKey.of(transaksjonId),
                    ResultJson(
                        failure = feilmelding.toJson(),
                    ).toJson(ResultJson.serializer()),
                )
            }
        }

        test("stopp flyt ved ikke aktivt arbeidsforhold") {
            val transaksjonId = UUID.randomUUID()
            val inntektsmelding = Mock.inntektsmelding

            testRapid.sendJson(
                Mock.steg0(transaksjonId),
            )

            mockStatic(OffsetDateTime::class) {
                every { OffsetDateTime.now() } returns inntektsmelding.mottatt

                testRapid.sendJson(
                    Mock
                        .steg1(transaksjonId)
                        .plus(Key.ARBEIDSFORHOLD to Mock.lagArbeidsforhold("123456789").toJson(Arbeidsforhold.serializer())),
                )
            }

            testRapid.inspektør.size shouldBeExactly 3

            verify {
                mockRedis.store.set(
                    RedisKey.of(transaksjonId),
                    ResultJson(
                        failure = "Mangler arbeidsforhold i perioden".toJson(),
                    ).toJson(ResultJson.serializer()),
                )
            }
        }
    })

private fun JsonElement.lesInntektsmelding(): Inntektsmelding =
    Key.SELVBESTEMT_INNTEKTSMELDING.lesOrNull(Inntektsmelding.serializer(), this.toMap()).shouldNotBeNull()

private object Mock {
    private const val ORG_NAVN = "Keiser Augustus' Ponniutleie"
    private val sykmeldt =
        Fnr.genererGyldig().let {
            Person(
                fnr = it,
                navn = "Ponnius Pilatus",
                foedselsdato = Person.foedselsdato(it),
            )
        }
    private val avsender =
        Fnr.genererGyldig().let {
            Person(
                fnr = it,
                navn = "King Kong Keiser",
                foedselsdato = Person.foedselsdato(it),
            )
        }

    val skjema =
        SkjemaInntektsmeldingSelvbestemt(
            selvbestemtId = UUID.randomUUID(),
            sykmeldtFnr = sykmeldt.fnr,
            avsender =
                SkjemaAvsender(
                    orgnr = Orgnr.genererGyldig(),
                    tlf = "43431234",
                ),
            sykmeldingsperioder =
                listOf(
                    14.mars til 14.april,
                ),
            agp =
                Arbeidsgiverperiode(
                    perioder =
                        listOf(
                            13.mars til 28.mars,
                        ),
                    egenmeldinger =
                        listOf(
                            13.mars til 13.mars,
                        ),
                    redusertLoennIAgp =
                        RedusertLoennIAgp(
                            beloep = 606.0,
                            begrunnelse = RedusertLoennIAgp.Begrunnelse.ManglerOpptjening,
                        ),
                ),
            inntekt =
                Inntekt(
                    beloep = 32100.0,
                    inntektsdato = 13.mars,
                    naturalytelser =
                        listOf(
                            Naturalytelse(
                                naturalytelse = Naturalytelse.Kode.AKSJERGRUNNFONDSBEVISTILUNDERKURS,
                                verdiBeloep = 4000.5,
                                sluttdato = 20.mars,
                            ),
                            Naturalytelse(
                                naturalytelse = Naturalytelse.Kode.LOSJI,
                                verdiBeloep = 700.0,
                                sluttdato = 22.mars,
                            ),
                        ),
                    endringAarsak = Ferietrekk,
                ),
            refusjon =
                Refusjon(
                    beloepPerMaaned = 12000.0,
                    endringer =
                        listOf(
                            RefusjonEndring(
                                beloep = 11000.0,
                                startdato = 15.mars,
                            ),
                            RefusjonEndring(
                                beloep = 1000.0,
                                startdato = 20.mars,
                            ),
                        ),
                    sluttdato = 30.mars,
                ),
        )

    val inntektsmelding =
        tilInntektsmelding(
            skjema = skjema,
            orgNavn = ORG_NAVN,
            sykmeldtNavn = sykmeldt.navn,
            avsenderNavn = avsender.navn,
        )

    fun steg0(transaksjonId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.SELVBESTEMT_IM_MOTTATT.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to "".toJson(),
            Key.SKJEMA_INNTEKTSMELDING to skjema.toJson(SkjemaInntektsmeldingSelvbestemt.serializer()),
            Key.ARBEIDSGIVER_FNR to avsender.fnr.toJson(),
        )

    fun steg1(transaksjonId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.SELVBESTEMT_IM_MOTTATT.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to "".toJson(),
            Key.VIRKSOMHET to ORG_NAVN.toJson(),
            Key.PERSONER to
                mapOf(
                    sykmeldt.fnr to sykmeldt,
                    avsender.fnr to avsender,
                ).toJson(personMapSerializer),
            Key.ARBEIDSFORHOLD to lagArbeidsforhold(orgnr = skjema.avsender.orgnr.verdi).toJson(Arbeidsforhold.serializer()),
        )

    fun steg2(
        transaksjonId: UUID,
        inntektsmelding: Inntektsmelding,
    ): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.SELVBESTEMT_IM_MOTTATT.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to "".toJson(),
            Key.SELVBESTEMT_INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
            Key.ER_DUPLIKAT_IM to false.toJson(Boolean.serializer()),
        )

    fun steg3(transaksjonId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.SELVBESTEMT_IM_MOTTATT.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to "".toJson(),
            Key.SAK_ID to "folkelig-lurendreier-sak-id".toJson(),
        )

    fun lagArbeidsforhold(orgnr: String) =
        listOf(
            Arbeidsforhold(
                arbeidsgiver = Arbeidsgiver("ORG", orgnr),
                ansettelsesperiode = Ansettelsesperiode(PeriodeNullable(LocalDate.MIN, LocalDate.MAX)),
                registrert = LocalDateTime.MIN,
            ),
        )
}
