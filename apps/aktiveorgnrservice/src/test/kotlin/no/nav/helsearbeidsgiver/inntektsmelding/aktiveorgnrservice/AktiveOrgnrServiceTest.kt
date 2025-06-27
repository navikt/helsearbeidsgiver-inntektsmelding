package no.nav.helsearbeidsgiver.inntektsmelding.aktiveorgnrservice

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.verify
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.AktiveArbeidsgivere
import no.nav.helsearbeidsgiver.felles.domene.PeriodeAapen
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.ansettelsesperioderSerializer
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateful
import no.nav.helsearbeidsgiver.felles.test.json.lesBehov
import no.nav.helsearbeidsgiver.felles.test.json.lesData
import no.nav.helsearbeidsgiver.felles.test.json.plusData
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedis
import no.nav.helsearbeidsgiver.felles.test.mock.mockFail
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.message
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class AktiveOrgnrServiceTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockRedis = MockRedis(RedisPrefix.AktiveOrgnr)

        ServiceRiverStateful(
            AktiveOrgnrService(testRapid, mockRedis.store),
        ).connect(testRapid)

        beforeEach {
            testRapid.reset()
            clearAllMocks()
            mockRedis.setup()
        }

        test("henter aktive orgnr") {
            val kontekstId = UUID.randomUUID()
            val orgnr = Orgnr.genererGyldig()
            val expectedSuccess = Mock.successResult(orgnr)

            testRapid.sendJson(
                Mock.startmelding(kontekstId),
            )

            testRapid.inspektør.size shouldBeExactly 3
            testRapid.message(0).also {
                it.lesBehov() shouldBe BehovType.ARBEIDSGIVERE
                Key.SVAR_KAFKA_KEY.lesOrNull(KafkaKey.serializer(), it.lesData()) shouldBe KafkaKey(Mock.sykmeldtFnr)
            }
            testRapid.message(1).also {
                it.lesBehov() shouldBe BehovType.HENT_ANSETTELSESPERIODER
                Key.SVAR_KAFKA_KEY.lesOrNull(KafkaKey.serializer(), it.lesData()) shouldBe KafkaKey(Mock.sykmeldtFnr)
            }
            testRapid.message(2).also {
                it.lesBehov() shouldBe BehovType.HENT_PERSONER
                Key.SVAR_KAFKA_KEY.lesOrNull(KafkaKey.serializer(), it.lesData()) shouldBe KafkaKey(Mock.sykmeldtFnr)
            }

            testRapid.sendJson(
                Mock.steg1Data(kontekstId, orgnr),
            )

            testRapid.inspektør.size shouldBeExactly 4
            testRapid.message(3).lesBehov() shouldBe BehovType.HENT_VIRKSOMHET_NAVN

            testRapid.sendJson(
                Mock.steg2Data(kontekstId, orgnr),
            )

            verify {
                mockRedis.store.skrivResultat(kontekstId, expectedSuccess)
            }
        }

        test("svarer med tom liste dersom sykmeldt mangler arbeidsforhold") {
            val kontekstId = UUID.randomUUID()
            val orgnr = Orgnr.genererGyldig()
            val expectedSuccess = Mock.successResultTomListe()

            testRapid.sendJson(
                Mock.startmelding(kontekstId),
            )

            testRapid.inspektør.size shouldBeExactly 3

            testRapid.sendJson(
                Mock
                    .steg1Data(kontekstId, orgnr)
                    .plusData(Key.ANSETTELSESPERIODER to JsonObject(emptyMap())),
            )

            // Virksomheter hentes ikke
            testRapid.inspektør.size shouldBeExactly 3

            verify {
                mockRedis.store.skrivResultat(kontekstId, expectedSuccess)
            }
        }

        test("svarer med tom liste dersom sykmeldtes arbeidsforhold og avsenders org-rettigheter ikke gjelder samme org") {
            val kontekstId = UUID.randomUUID()
            val orgnr = Orgnr.genererGyldig()
            val expectedSuccess = Mock.successResultTomListe()

            testRapid.sendJson(
                Mock.startmelding(kontekstId),
            )

            testRapid.inspektør.size shouldBeExactly 3

            testRapid.sendJson(
                Mock
                    .steg1Data(kontekstId, orgnr)
                    .plusData(Key.ORG_RETTIGHETER to setOf(Orgnr.genererGyldig().verdi).toJson(String.serializer())),
            )

            // Orgnavn hentes ikke
            testRapid.inspektør.size shouldBeExactly 3

            verify {
                mockRedis.store.skrivResultat(kontekstId, expectedSuccess)
            }
        }

        test("svarer med feilmelding dersom avsender mangler org-rettigheter") {
            val kontekstId = UUID.randomUUID()
            val orgnr = Orgnr.genererGyldig()
            val feilmelding = "Må ha orgrettigheter for å kunne hente virksomheter."
            val expectedFailure = Mock.failureResult(feilmelding)

            testRapid.sendJson(
                Mock.startmelding(kontekstId),
            )

            testRapid.inspektør.size shouldBeExactly 3

            testRapid.sendJson(
                Mock
                    .steg1Data(kontekstId, orgnr)
                    .plusData(Key.ORG_RETTIGHETER to emptySet<String>().toJson(String.serializer())),
            )

            // Orgnavn hentes ikke
            testRapid.inspektør.size shouldBeExactly 3

            verify {
                mockRedis.store.skrivResultat(kontekstId, expectedFailure)
            }
        }

        test("svarer med feilmelding dersom man ikke klarer å hente noe") {
            val fail =
                mockFail(
                    feilmelding = "Kafka streiker for bedre vilkår :(",
                    eventName = EventName.AKTIVE_ORGNR_REQUESTED,
                    behovType = BehovType.ARBEIDSGIVERE,
                )
            val expectedFailure = Mock.failureResult(fail.feilmelding)

            testRapid.sendJson(
                Mock.startmelding(fail.kontekstId),
            )

            testRapid.sendJson(fail.tilMelding())

            verify {
                mockRedis.store.skrivResultat(fail.kontekstId, expectedFailure)
            }
        }
    })

private object Mock {
    val sykmeldtFnr = Fnr.genererGyldig()

    private const val SYKMELDT_NAVN = "Ole Idole"
    private const val AVSENDER_NAVN = "Ole Jacob Evenrud"
    private const val ORG_NAVN = "Mexican Standup A/S"

    private val avsenderFnr = Fnr.genererGyldig()

    private val personer =
        listOf(
            sykmeldtFnr to SYKMELDT_NAVN,
            avsenderFnr to AVSENDER_NAVN,
        ).associate { (fnr, navn) ->
            fnr to Person(fnr, navn)
        }

    fun successResult(orgnr: Orgnr): ResultJson =
        ResultJson(
            success =
                AktiveArbeidsgivere(
                    sykmeldtNavn = SYKMELDT_NAVN,
                    avsenderNavn = AVSENDER_NAVN,
                    arbeidsgivere =
                        listOf(
                            AktiveArbeidsgivere.Arbeidsgiver(
                                orgnr = orgnr,
                                orgNavn = ORG_NAVN,
                            ),
                        ),
                ).toJson(AktiveArbeidsgivere.serializer()),
        )

    fun successResultTomListe(): ResultJson =
        ResultJson(
            success =
                AktiveArbeidsgivere(
                    sykmeldtNavn = SYKMELDT_NAVN,
                    avsenderNavn = AVSENDER_NAVN,
                    arbeidsgivere = emptyList(),
                ).toJson(AktiveArbeidsgivere.serializer()),
        )

    fun failureResult(feilmelding: String): ResultJson =
        ResultJson(
            failure = feilmelding.toJson(),
        )

    fun startmelding(kontekstId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.AKTIVE_ORGNR_REQUESTED.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FNR to sykmeldtFnr.toJson(),
                    Key.ARBEIDSGIVER_FNR to avsenderFnr.toJson(),
                ).toJson(),
        )

    fun steg1Data(
        kontekstId: UUID,
        orgnr: Orgnr,
    ): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.AKTIVE_ORGNR_REQUESTED.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.ANSETTELSESPERIODER to
                        mapOf(
                            orgnr to setOf(PeriodeAapen(12.mars, 13.mars)),
                        ).toJson(ansettelsesperioderSerializer),
                    Key.ORG_RETTIGHETER to setOf(orgnr).toJson(Orgnr.serializer()),
                    Key.PERSONER to personer.toJson(personMapSerializer),
                ).toJson(),
        )

    fun steg2Data(
        kontekstId: UUID,
        orgnr: Orgnr,
    ): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.AKTIVE_ORGNR_REQUESTED.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.VIRKSOMHETER to mapOf(orgnr.verdi to ORG_NAVN).toJson(),
                ).toJson(),
        )
}
