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
import no.nav.helsearbeidsgiver.felles.domene.Ansettelsesperiode
import no.nav.helsearbeidsgiver.felles.domene.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.domene.Arbeidsgiver
import no.nav.helsearbeidsgiver.felles.domene.PeriodeNullable
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisPrefix
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceRiverStateful
import no.nav.helsearbeidsgiver.felles.test.json.lesBehov
import no.nav.helsearbeidsgiver.felles.test.json.plusData
import no.nav.helsearbeidsgiver.felles.test.mock.MockRedis
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.message
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.kl
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
            val transaksjonId = UUID.randomUUID()
            val orgnr = Orgnr.genererGyldig()
            val expectedSuccess = Mock.successResult(orgnr)

            testRapid.sendJson(
                Mock.startmelding(transaksjonId),
            )

            testRapid.inspektør.size shouldBeExactly 3
            testRapid.message(0).lesBehov() shouldBe BehovType.ARBEIDSGIVERE
            testRapid.message(1).lesBehov() shouldBe BehovType.HENT_ARBEIDSFORHOLD
            testRapid.message(2).lesBehov() shouldBe BehovType.HENT_PERSONER

            testRapid.sendJson(
                Mock.steg1Data(transaksjonId, orgnr),
            )

            testRapid.inspektør.size shouldBeExactly 4
            testRapid.message(3).lesBehov() shouldBe BehovType.HENT_VIRKSOMHET_NAVN

            testRapid.sendJson(
                Mock.steg2Data(transaksjonId, orgnr),
            )

            verify {
                mockRedis.store.skrivResultat(transaksjonId, expectedSuccess)
            }
        }

        test("svarer med tom liste dersom sykmeldt mangler arbeidsforhold") {
            val transaksjonId = UUID.randomUUID()
            val orgnr = Orgnr.genererGyldig()
            val expectedSuccess = Mock.successResultTomListe()

            testRapid.sendJson(
                Mock.startmelding(transaksjonId),
            )

            testRapid.inspektør.size shouldBeExactly 3

            testRapid.sendJson(
                Mock
                    .steg1Data(transaksjonId, orgnr)
                    .plusData(Key.ARBEIDSFORHOLD to emptyList<Arbeidsforhold>().toJson(Arbeidsforhold.serializer())),
            )

            // Virksomheter hentes ikke
            testRapid.inspektør.size shouldBeExactly 3

            verify {
                mockRedis.store.skrivResultat(transaksjonId, expectedSuccess)
            }
        }

        test("svarer med tom liste dersom sykmeldtes arbeidsforhold og avsenders org-rettigheter ikke gjelder samme org") {
            val transaksjonId = UUID.randomUUID()
            val orgnr = Orgnr.genererGyldig()
            val expectedSuccess = Mock.successResultTomListe()

            testRapid.sendJson(
                Mock.startmelding(transaksjonId),
            )

            testRapid.inspektør.size shouldBeExactly 3

            testRapid.sendJson(
                Mock
                    .steg1Data(transaksjonId, orgnr)
                    .plusData(Key.ORG_RETTIGHETER to setOf(Orgnr.genererGyldig().verdi).toJson(String.serializer())),
            )

            // Orgnavn hentes ikke
            testRapid.inspektør.size shouldBeExactly 3

            verify {
                mockRedis.store.skrivResultat(transaksjonId, expectedSuccess)
            }
        }

        test("svarer med feilmelding dersom avsender mangler org-rettigheter") {
            val transaksjonId = UUID.randomUUID()
            val orgnr = Orgnr.genererGyldig()
            val feilmelding = "Må ha orgrettigheter for å kunne hente virksomheter."
            val expectedFailure = Mock.failureResult(feilmelding)

            testRapid.sendJson(
                Mock.startmelding(transaksjonId),
            )

            testRapid.inspektør.size shouldBeExactly 3

            testRapid.sendJson(
                Mock
                    .steg1Data(transaksjonId, orgnr)
                    .plusData(Key.ORG_RETTIGHETER to emptySet<String>().toJson(String.serializer())),
            )

            // Orgnavn hentes ikke
            testRapid.inspektør.size shouldBeExactly 3

            verify {
                mockRedis.store.skrivResultat(transaksjonId, expectedFailure)
            }
        }

        test("svarer med feilmelding dersom man ikke klarer å hente noe") {
            val transaksjonId = UUID.randomUUID()
            val feilmelding = "Kafka streiker for bedre vilkår :("
            val expectedFailure = Mock.failureResult(feilmelding)

            testRapid.sendJson(
                Mock.startmelding(transaksjonId),
            )

            testRapid.sendJson(
                Fail(
                    feilmelding = feilmelding,
                    event = EventName.AKTIVE_ORGNR_REQUESTED,
                    transaksjonId = transaksjonId,
                    forespoerselId = null,
                    utloesendeMelding =
                        JsonObject(
                            mapOf(
                                Key.BEHOV.toString() to BehovType.ARBEIDSGIVERE.toJson(),
                            ),
                        ),
                ).tilMelding(),
            )

            verify {
                mockRedis.store.skrivResultat(transaksjonId, expectedFailure)
            }
        }
    })

private object Mock {
    private const val SYKMELDT_NAVN = "Ole Idole"
    private const val AVSENDER_NAVN = "Ole Jacob Evenrud"
    private const val ORG_NAVN = "Mexican Standup A/S"

    private val sykmeldtFnr = Fnr.genererGyldig()
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
                    fulltNavn = SYKMELDT_NAVN,
                    avsenderNavn = AVSENDER_NAVN,
                    underenheter =
                        listOf(
                            AktiveArbeidsgivere.Arbeidsgiver(
                                orgnrUnderenhet = orgnr.verdi,
                                virksomhetsnavn = ORG_NAVN,
                            ),
                        ),
                ).toJson(AktiveArbeidsgivere.serializer()),
        )

    fun successResultTomListe(): ResultJson =
        ResultJson(
            success =
                AktiveArbeidsgivere(
                    fulltNavn = SYKMELDT_NAVN,
                    avsenderNavn = AVSENDER_NAVN,
                    underenheter = emptyList(),
                ).toJson(AktiveArbeidsgivere.serializer()),
        )

    fun failureResult(feilmelding: String): ResultJson =
        ResultJson(
            failure = feilmelding.toJson(),
        )

    fun startmelding(transaksjonId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.AKTIVE_ORGNR_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FNR to sykmeldtFnr.toJson(),
                    Key.ARBEIDSGIVER_FNR to avsenderFnr.toJson(),
                ).toJson(),
        )

    fun steg1Data(
        transaksjonId: UUID,
        orgnr: Orgnr,
    ): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.AKTIVE_ORGNR_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.ARBEIDSFORHOLD to
                        listOf(
                            Arbeidsforhold(
                                arbeidsgiver =
                                    Arbeidsgiver(
                                        type = "ORG",
                                        organisasjonsnummer = orgnr.verdi,
                                    ),
                                ansettelsesperiode = Ansettelsesperiode(PeriodeNullable(12.mars, 13.mars)),
                                registrert = 12.mars.kl(13, 1, 2, 3),
                            ),
                        ).toJson(Arbeidsforhold.serializer()),
                    Key.ORG_RETTIGHETER to setOf(orgnr).toJson(Orgnr.serializer()),
                    Key.PERSONER to personer.toJson(personMapSerializer),
                ).toJson(),
        )

    fun steg2Data(
        transaksjonId: UUID,
        orgnr: Orgnr,
    ): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.AKTIVE_ORGNR_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.VIRKSOMHETER to mapOf(orgnr.verdi to ORG_NAVN).toJson(),
                ).toJson(),
        )
}
