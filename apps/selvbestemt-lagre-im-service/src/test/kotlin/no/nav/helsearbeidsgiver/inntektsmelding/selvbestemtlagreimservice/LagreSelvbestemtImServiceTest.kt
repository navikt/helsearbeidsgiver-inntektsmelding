package no.nav.helsearbeidsgiver.inntektsmelding.selvbestemtlagreimservice

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
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
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Arbeidsgiverperiode
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Ferietrekk
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntekt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Naturalytelse
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.RedusertLoennIAgp
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Refusjon
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.RefusjonEndring
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.ArbeidsforholdType
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaAvsender
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmeldingSelvbestemt
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.PeriodeAapen
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.ansettelsesperioderSerializer
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateful
import no.nav.helsearbeidsgiver.felles.test.json.lesBehov
import no.nav.helsearbeidsgiver.felles.test.json.lesData
import no.nav.helsearbeidsgiver.felles.test.json.minusData
import no.nav.helsearbeidsgiver.felles.test.json.plusData
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedis
import no.nav.helsearbeidsgiver.felles.test.mock.mockFail
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.message
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.kl
import no.nav.helsearbeidsgiver.utils.test.date.mai
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.date.november
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.OffsetDateTime
import java.util.UUID

class LagreSelvbestemtImServiceTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockRedis = MockRedis(RedisPrefix.LagreSelvbestemtIm)

        ServiceRiverStateful(
            LagreSelvbestemtImService(testRapid, mockRedis.store),
        ).connect(testRapid)

        beforeTest {
            testRapid.reset()
            clearAllMocks()
            mockRedis.setup()
        }

        context("Inntektsmeldinger AarsakInnsending.Ny lagres og sak oprettes") {

            fun ArbeidsforholdType.skalHaArbeidsforhold(): Boolean =
                when (this) {
                    is ArbeidsforholdType.MedArbeidsforhold -> true
                    is ArbeidsforholdType.UtenArbeidsforhold, is ArbeidsforholdType.Fisker -> false
                }

            withData(
                nameFn = { "med skjema ${ArbeidsforholdType::class.simpleName}.${it::class.simpleName}" },
                ts =
                    setOf(
                        Mock.skjema.arbeidsforholdType,
                        ArbeidsforholdType.Fisker,
                        ArbeidsforholdType.UtenArbeidsforhold,
                    ),
            ) { arbeidsforholdType ->

                val skjema = Mock.skjema.copy(selvbestemtId = null, arbeidsforholdType = arbeidsforholdType)

                val inntektsmeldingType = arbeidsforholdType.tilInntektsmeldingType(UUID.randomUUID())
                val nyInntektsmelding = Mock.inntektsmelding.copy(aarsakInnsending = AarsakInnsending.Ny, type = inntektsmeldingType)

                val kontekstId = UUID.randomUUID()

                testRapid.sendJson(
                    Mock
                        .steg0(kontekstId)
                        .plusData(
                            Pair(
                                Key.SKJEMA_INNTEKTSMELDING,
                                skjema
                                    .copy(selvbestemtId = null)
                                    .toJson(SkjemaInntektsmeldingSelvbestemt.serializer()),
                            ),
                        ),
                )

                testRapid.inspektør.size shouldBeExactly 3
                testRapid.message(0).also {
                    it.lesBehov() shouldBe BehovType.HENT_VIRKSOMHET_NAVN
                    Key.SVAR_KAFKA_KEY.lesOrNull(KafkaKey.serializer(), it.lesData()) shouldBe KafkaKey(skjema.sykmeldtFnr)
                }
                testRapid.message(1).also {
                    it.lesBehov() shouldBe BehovType.HENT_PERSONER
                    Key.SVAR_KAFKA_KEY.lesOrNull(KafkaKey.serializer(), it.lesData()) shouldBe KafkaKey(skjema.sykmeldtFnr)
                }
                testRapid.message(2).also {
                    it.lesBehov() shouldBe BehovType.HENT_ARBEIDSFORHOLD
                    Key.SVAR_KAFKA_KEY.lesOrNull(KafkaKey.serializer(), it.lesData()) shouldBe KafkaKey(skjema.sykmeldtFnr)
                }

                val arbeidsforholdListe =
                    if (arbeidsforholdType.skalHaArbeidsforhold()) {
                        Mock.lagAnsettelsesperioder(orgnr = Mock.skjema.avsender.orgnr.verdi)
                    } else {
                        emptyMap()
                    }

                mockStatic(OffsetDateTime::class) {
                    every { OffsetDateTime.now() } returns nyInntektsmelding.mottatt

                    testRapid.sendJson(
                        Mock.steg1(kontekstId).plusData(Key.ANSETTELSESPERIODER to arbeidsforholdListe.toJson(ansettelsesperioderSerializer)),
                    )
                }

                testRapid.inspektør.size shouldBeExactly 4
                testRapid.message(3).toMap().also {
                    Key.BEHOV.lesOrNull(BehovType.serializer(), it) shouldBe BehovType.LAGRE_SELVBESTEMT_IM
                    it.lesInntektsmelding().shouldBeEqualToIgnoringFields(nyInntektsmelding, Inntektsmelding::id, Inntektsmelding::type)

                    val type = it.lesInntektsmelding().type
                    type.shouldBeEqualToIgnoringFields(inntektsmeldingType, Inntektsmelding.Type::id)
                    type.id shouldNotBe nyInntektsmelding.type.id
                }

                testRapid.sendJson(Mock.steg2(kontekstId, nyInntektsmelding))

                testRapid.inspektør.size shouldBeExactly 5
                testRapid.message(4).lesBehov() shouldBe BehovType.OPPRETT_SELVBESTEMT_SAK

                testRapid.sendJson(Mock.steg3(kontekstId))

                testRapid.inspektør.size shouldBeExactly 6
                testRapid.message(5).toMap().also {
                    Key.EVENT_NAME.lesOrNull(EventName.serializer(), it) shouldBe EventName.SELVBESTEMT_IM_LAGRET
                    Key.KONTEKST_ID.lesOrNull(UuidSerializer, it) shouldBe kontekstId

                    it.lesInntektsmelding().shouldBeEqualToIgnoringFields(nyInntektsmelding, Inntektsmelding::id)
                }

                verify {
                    mockRedis.store.skrivResultat(
                        kontekstId,
                        ResultJson(
                            success = nyInntektsmelding.type.id.toJson(),
                        ),
                    )
                }
            }
        }

        test("endret inntektsmelding lagres uten at sak opprettes") {
            val kontekstId = UUID.randomUUID()
            val endretInntektsmelding = Mock.inntektsmelding.copy(aarsakInnsending = AarsakInnsending.Endring)

            testRapid.sendJson(
                Mock.steg0(kontekstId),
            )

            testRapid.inspektør.size shouldBeExactly 3
            testRapid.message(0).also {
                it.lesBehov() shouldBe BehovType.HENT_VIRKSOMHET_NAVN
                Key.SVAR_KAFKA_KEY.lesOrNull(KafkaKey.serializer(), it.lesData()) shouldBe KafkaKey(Mock.skjema.sykmeldtFnr)
            }
            testRapid.message(1).also {
                it.lesBehov() shouldBe BehovType.HENT_PERSONER
                Key.SVAR_KAFKA_KEY.lesOrNull(KafkaKey.serializer(), it.lesData()) shouldBe KafkaKey(Mock.skjema.sykmeldtFnr)
            }
            testRapid.message(2).also {
                it.lesBehov() shouldBe BehovType.HENT_ANSETTELSESPERIODER
                Key.SVAR_KAFKA_KEY.lesOrNull(KafkaKey.serializer(), it.lesData()) shouldBe KafkaKey(Mock.skjema.sykmeldtFnr)
            }

            mockStatic(OffsetDateTime::class) {
                every { OffsetDateTime.now() } returns endretInntektsmelding.mottatt

                testRapid.sendJson(
                    Mock.steg1(kontekstId),
                )
            }

            testRapid.inspektør.size shouldBeExactly 4
            testRapid.message(3).toMap().also {
                Key.BEHOV.lesOrNull(BehovType.serializer(), it) shouldBe BehovType.LAGRE_SELVBESTEMT_IM
                it.lesInntektsmelding().shouldBeEqualToIgnoringFields(endretInntektsmelding, Inntektsmelding::id)
            }

            testRapid.sendJson(
                Mock.steg2(kontekstId, endretInntektsmelding),
            )

            testRapid.inspektør.size shouldBeExactly 5
            testRapid.message(4).toMap().also {
                Key.EVENT_NAME.lesOrNull(EventName.serializer(), it) shouldBe EventName.SELVBESTEMT_IM_LAGRET
                Key.KONTEKST_ID.lesOrNull(UuidSerializer, it) shouldBe kontekstId

                it.lesInntektsmelding().shouldBeEqualToIgnoringFields(endretInntektsmelding, Inntektsmelding::id)
            }

            verify {
                mockRedis.store.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success = endretInntektsmelding.type.id.toJson(),
                    ),
                )
            }
        }

        test("duplikat inntektsmelding lagres uten at sluttevent publiseres") {
            val kontekstId = UUID.randomUUID()
            val duplikatInntektsmelding = Mock.inntektsmelding.copy(aarsakInnsending = AarsakInnsending.Endring)

            testRapid.sendJson(
                Mock.steg0(kontekstId),
            )

            mockStatic(OffsetDateTime::class) {
                every { OffsetDateTime.now() } returns duplikatInntektsmelding.mottatt

                testRapid.sendJson(
                    Mock.steg1(kontekstId),
                )
            }

            testRapid.inspektør.size shouldBeExactly 4
            testRapid.message(3).toMap().also {
                Key.BEHOV.lesOrNull(BehovType.serializer(), it) shouldBe BehovType.LAGRE_SELVBESTEMT_IM
                it.lesInntektsmelding().shouldBeEqualToIgnoringFields(duplikatInntektsmelding, Inntektsmelding::id)
            }

            testRapid.sendJson(
                Mock
                    .steg2(kontekstId, duplikatInntektsmelding)
                    .plusData(Key.ER_DUPLIKAT_IM to true.toJson(Boolean.serializer())),
            )

            testRapid.inspektør.size shouldBeExactly 4
            Key.EVENT_NAME.lesOrNull(
                EventName.serializer(),
                testRapid.message(3).toMap(),
            ) shouldNotBe EventName.SELVBESTEMT_IM_LAGRET

            verify {
                mockRedis.store.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success = duplikatInntektsmelding.type.id.toJson(),
                    ),
                )
            }
        }

        test("bruk defaultverdier ved håndterbare feil under henting av data") {
            val kontekstId = UUID.randomUUID()

            val inntektsmeldingMedDefaults =
                Mock.inntektsmelding.let {
                    it.copy(
                        sykmeldt =
                            it.sykmeldt.copy(
                                navn = "",
                            ),
                        avsender =
                            it.avsender.copy(
                                orgNavn = "Ukjent virksomhet",
                                navn = "",
                            ),
                        aarsakInnsending = AarsakInnsending.Endring,
                    )
                }

            testRapid.sendJson(
                Mock.steg0(kontekstId),
            )
            testRapid.sendJson(
                Mock.steg1(kontekstId).minusData(Key.VIRKSOMHETER, Key.PERSONER),
            )

            testRapid.sendJson(
                mockFail(
                    feilmelding = "Denne bedriften er skummel.",
                    eventName = EventName.SELVBESTEMT_IM_MOTTATT,
                    behovType = BehovType.HENT_VIRKSOMHET_NAVN,
                ).let {
                    it.copy(
                        kontekstId = kontekstId,
                        utloesendeMelding =
                            it.utloesendeMelding.plus(
                                Key.KONTEKST_ID to kontekstId.toJson(),
                            ),
                    )
                }.tilMelding(),
            )

            mockStatic(OffsetDateTime::class) {
                every { OffsetDateTime.now() } returns inntektsmeldingMedDefaults.mottatt

                testRapid.sendJson(
                    mockFail(
                        feilmelding = "Denne personen jobber i PST.",
                        eventName = EventName.SELVBESTEMT_IM_MOTTATT,
                        behovType = BehovType.HENT_PERSONER,
                    ).let {
                        it.copy(
                            kontekstId = kontekstId,
                            utloesendeMelding =
                                it.utloesendeMelding.plus(
                                    Key.KONTEKST_ID to kontekstId.toJson(),
                                ),
                        )
                    }.tilMelding(),
                )
            }

            testRapid.inspektør.size shouldBeExactly 4
            testRapid.message(3).toMap().also {
                Key.BEHOV.lesOrNull(BehovType.serializer(), it) shouldBe BehovType.LAGRE_SELVBESTEMT_IM
                it.lesInntektsmelding().shouldBeEqualToIgnoringFields(inntektsmeldingMedDefaults, Inntektsmelding::id)
            }

            testRapid.sendJson(
                Mock.steg2(kontekstId, inntektsmeldingMedDefaults),
            )

            testRapid.inspektør.size shouldBeExactly 5
            testRapid.message(4).toMap().also {
                Key.EVENT_NAME.lesOrNull(EventName.serializer(), it) shouldBe EventName.SELVBESTEMT_IM_LAGRET
                Key.KONTEKST_ID.lesOrNull(UuidSerializer, it) shouldBe kontekstId

                it.lesInntektsmelding().shouldBeEqualToIgnoringFields(inntektsmeldingMedDefaults, Inntektsmelding::id)
            }

            verify {
                mockRedis.store.skrivMellomlagring(
                    kontekstId,
                    Key.VIRKSOMHETER,
                    emptyMap<String, String>().toJson(),
                )

                mockRedis.store.skrivMellomlagring(
                    kontekstId,
                    Key.PERSONER,
                    emptyMap<String, JsonElement>().toJson(),
                )

                mockRedis.store.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success = inntektsmeldingMedDefaults.type.id.toJson(),
                    ),
                )
            }
        }

        test("svar med feilmelding ved uhåndterbare feil") {
            val fail =
                mockFail(
                    feilmelding = "Databasen er full :(",
                    eventName = EventName.SELVBESTEMT_IM_MOTTATT,
                    behovType = BehovType.LAGRE_SELVBESTEMT_IM,
                )
            val endretInntektsmelding = Mock.inntektsmelding.copy(aarsakInnsending = AarsakInnsending.Endring)

            testRapid.sendJson(
                Mock.steg0(fail.kontekstId),
            )

            mockStatic(OffsetDateTime::class) {
                every { OffsetDateTime.now() } returns endretInntektsmelding.mottatt

                testRapid.sendJson(
                    Mock.steg1(fail.kontekstId),
                )
            }

            testRapid.sendJson(fail.tilMelding())

            testRapid.inspektør.size shouldBeExactly 4
            testRapid.message(3).toMap().also {
                Key.BEHOV.lesOrNull(BehovType.serializer(), it) shouldBe BehovType.LAGRE_SELVBESTEMT_IM
                it.lesInntektsmelding().shouldBeEqualToIgnoringFields(endretInntektsmelding, Inntektsmelding::id)
            }

            verify {
                mockRedis.store.skrivResultat(
                    fail.kontekstId,
                    ResultJson(
                        failure = fail.feilmelding.toJson(),
                    ),
                )
            }
        }

        test("stopp flyt ved skjema.arbeidsforholdType.MedArbeidsforhold men ikke aktivt arbeidsforhold i aareg") {
            val kontekstId = UUID.randomUUID()
            val inntektsmelding = Mock.inntektsmelding

            testRapid.sendJson(
                Mock.steg0(kontekstId),
            )

            mockStatic(OffsetDateTime::class) {
                every { OffsetDateTime.now() } returns inntektsmelding.mottatt

                testRapid.sendJson(
                    Mock
                        .steg1(kontekstId)
                        .plusData(Key.ANSETTELSESPERIODER to Mock.lagAnsettelsesperioder(Orgnr.genererGyldig()).toJson(ansettelsesperioderSerializer)),
                )
            }

            testRapid.inspektør.size shouldBeExactly 3

            verify {
                mockRedis.store.skrivResultat(
                    kontekstId,
                    ResultJson(
                        failure = "Mangler arbeidsforhold i perioden".toJson(),
                    ),
                )
            }
        }
    })

private fun Map<Key, JsonElement>.lesInntektsmelding(): Inntektsmelding {
    val data = this[Key.DATA].shouldNotBeNull().toMap()
    return Key.SELVBESTEMT_INNTEKTSMELDING.lesOrNull(Inntektsmelding.serializer(), data).shouldNotBeNull()
}

private object Mock {
    private const val ORG_NAVN = "Keiser Augustus' Ponniutleie"
    private val sykmeldt =
        Person(
            fnr = Fnr.genererGyldig(),
            navn = "Ponnius Pilatus",
        )
    private val avsender =
        Person(
            fnr = Fnr.genererGyldig(),
            navn = "King Kong Keiser",
        )

    val vedtaksperiodeId: UUID = UUID.randomUUID()

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
                    endringAarsaker = listOf(Ferietrekk),
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
                            RefusjonEndring(
                                beloep = 0.0,
                                startdato = 30.mars,
                            ),
                        ),
                ),
            vedtaksperiodeId = vedtaksperiodeId,
            arbeidsforholdType = ArbeidsforholdType.MedArbeidsforhold(vedtaksperiodeId = vedtaksperiodeId),
        )

    val inntektsmelding =
        tilInntektsmelding(
            skjema = skjema,
            orgNavn = ORG_NAVN,
            sykmeldtNavn = sykmeldt.navn,
            avsenderNavn = avsender.navn,
            mottatt = 16.august.kl(18, 19, 0, 0),
        )

    fun steg0(kontekstId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.SELVBESTEMT_IM_MOTTATT.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.ARBEIDSGIVER_FNR to avsender.fnr.toJson(),
                    Key.SKJEMA_INNTEKTSMELDING to skjema.toJson(SkjemaInntektsmeldingSelvbestemt.serializer()),
                    Key.MOTTATT to inntektsmelding.mottatt.toLocalDateTime().toJson(),
                ).toJson(),
        )

    fun steg1(kontekstId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.SELVBESTEMT_IM_MOTTATT.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.VIRKSOMHETER to mapOf(skjema.avsender.orgnr.verdi to ORG_NAVN).toJson(),
                    Key.PERSONER to
                        mapOf(
                            sykmeldt.fnr to sykmeldt,
                            avsender.fnr to avsender,
                        ).toJson(personMapSerializer),
                    Key.ANSETTELSESPERIODER to lagAnsettelsesperioder(skjema.avsender.orgnr).toJson(ansettelsesperioderSerializer),
                ).toJson(),
        )

    fun steg2(
        kontekstId: UUID,
        inntektsmelding: Inntektsmelding,
    ): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.SELVBESTEMT_IM_MOTTATT.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.SELVBESTEMT_INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
                    Key.ER_DUPLIKAT_IM to false.toJson(Boolean.serializer()),
                ).toJson(),
        )

    fun steg3(kontekstId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.SELVBESTEMT_IM_MOTTATT.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.SAK_ID to "folkelig-lurendreier-sak-id".toJson(),
                ).toJson(),
        )

    fun lagAnsettelsesperioder(orgnr: Orgnr) =
        mapOf(
            orgnr to
                setOf(
                    PeriodeAapen(
                        1.mai(2016),
                        30.november(2024),
                    ),
                ),
        )
}
