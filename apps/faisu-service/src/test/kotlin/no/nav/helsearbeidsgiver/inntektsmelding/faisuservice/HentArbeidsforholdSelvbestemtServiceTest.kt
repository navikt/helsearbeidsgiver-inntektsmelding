package no.nav.helsearbeidsgiver.inntektsmelding.faisuservice

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verifySequence
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Ansettelsesforhold
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.test.json.lesBehov
import no.nav.hag.simba.utils.felles.test.json.plusData
import no.nav.hag.simba.utils.felles.test.mock.mockFail
import no.nav.hag.simba.utils.rr.service.ServiceRiverStateless
import no.nav.hag.simba.utils.rr.test.message
import no.nav.hag.simba.utils.rr.test.mockConnectToRapid
import no.nav.hag.simba.utils.rr.test.sendJson
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.juni
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class HentArbeidsforholdSelvbestemtServiceTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockRedisStore = mockk<RedisStore>(relaxed = true)

        mockConnectToRapid(testRapid) {
            listOf(
                ServiceRiverStateless(
                    HentArbeidsforholdSelvbestemtService(it, mockRedisStore),
                ),
            )
        }

        beforeEach {
            testRapid.reset()
            clearAllMocks()
        }

        test("henter arbeidsforhold") {
            val kontekstId = UUID.randomUUID()

            val ansettelsesforholdUtenforFilter =
                listOf(
                    Ansettelsesforhold(
                        startdato = 2.februar,
                        sluttdato = 10.februar,
                        yrkeskode = "1111111",
                        yrkesbeskrivelse = "LEGE",
                        stillingsprosent = 100.0,
                    ),
                    Ansettelsesforhold(startdato = 8.april, sluttdato = 30.juni, yrkeskode = "2222222", yrkesbeskrivelse = "TANNLEGE", stillingsprosent = 50.0),
                    Ansettelsesforhold(startdato = 9.april, sluttdato = null, yrkeskode = "3333333", yrkesbeskrivelse = "KOKK", stillingsprosent = 80.0),
                )
            val ansettelsesforholdInnenforFilter =
                listOf(
                    Ansettelsesforhold(
                        startdato = 11.februar,
                        sluttdato = 15.mars,
                        yrkeskode = "4444444",
                        yrkesbeskrivelse = "BARNEHAGEASSISTENT",
                        stillingsprosent = 100.0,
                    ),
                    Ansettelsesforhold(
                        startdato = 10.mars,
                        sluttdato = 1.april,
                        yrkeskode = "5555555",
                        yrkesbeskrivelse = "SYKEPLEIER",
                        stillingsprosent = 80.0,
                    ),
                    Ansettelsesforhold(
                        startdato = 3.april,
                        sluttdato = null,
                        yrkeskode = "6666666",
                        yrkesbeskrivelse = "HJELPEPLEIER",
                        stillingsprosent = 50.0,
                    ),
                    Ansettelsesforhold(
                        startdato = 6.april,
                        sluttdato = 16.april,
                        yrkeskode = "7777777",
                        yrkesbeskrivelse = "RENHOLDER",
                        stillingsprosent = 60.0,
                    ),
                    Ansettelsesforhold(
                        startdato = 1.januar,
                        sluttdato = 1.juni,
                        yrkeskode = "8888888",
                        yrkesbeskrivelse = "VAKTMESTER",
                        stillingsprosent = 70.0,
                    ),
                )
            val ansettelsesforholdPerOrgnr =
                mapOf(
                    Mock.orgnr to ansettelsesforholdUtenforFilter + ansettelsesforholdInnenforFilter,
                    Orgnr.genererGyldig() to
                        listOf(
                            Ansettelsesforhold(
                                startdato = 20.mars,
                                sluttdato = 30.mars,
                                yrkeskode = "9999999",
                                yrkesbeskrivelse = "LÆRER",
                                stillingsprosent = 100.0,
                            ),
                            Ansettelsesforhold(
                                startdato = 1.april,
                                sluttdato = null,
                                yrkeskode = "1010101",
                                yrkesbeskrivelse = "FORSKER",
                                stillingsprosent = 90.0,
                            ),
                        ),
                )

            testRapid.sendJson(Mock.steg0(kontekstId))

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.message(0).lesBehov() shouldBe BehovType.HENT_ANSETTELSESPERIODER

            testRapid.sendJson(Mock.steg1(kontekstId, ansettelsesforholdPerOrgnr))

            testRapid.inspektør.size shouldBeExactly 1
            verifySequence {
                mockRedisStore.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success = ansettelsesforholdInnenforFilter.toJson(Ansettelsesforhold.serializer().list()),
                    ),
                )
            }
        }

        test("tåler ingen ansettelsesforhold funnet") {
            val kontekstId = UUID.randomUUID()

            testRapid.sendJson(Mock.steg1(kontekstId, emptyMap()))

            verifySequence {
                mockRedisStore.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success = emptyList<Ansettelsesforhold>().toJson(Ansettelsesforhold.serializer().list()),
                    ),
                )
            }
        }

        test("tåler ingen ansettelsesforhold for gitt orgnr") {
            val kontekstId = UUID.randomUUID()

            val ansettelsesforholdInnenforFilter =
                listOf(
                    Ansettelsesforhold(
                        startdato = 10.mars,
                        sluttdato = 1.april,
                        yrkeskode = "1234567",
                        yrkesbeskrivelse = "BARNEHAGEASSISTENT",
                        stillingsprosent = 100.0,
                    ),
                    Ansettelsesforhold(startdato = 3.april, sluttdato = null, yrkeskode = "7654321", yrkesbeskrivelse = "SYKEPLEIER", stillingsprosent = 80.0),
                )
            val ansettelsesforholdPerOrgnr =
                mapOf(
                    Orgnr.genererGyldig() to ansettelsesforholdInnenforFilter,
                )

            testRapid.sendJson(Mock.steg1(kontekstId, ansettelsesforholdPerOrgnr))

            verifySequence {
                mockRedisStore.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success = emptyList<Ansettelsesforhold>().toJson(Ansettelsesforhold.serializer().list()),
                    ),
                )
            }
        }

        test("tåler ingen ansettelsesforhold for gitt periode") {
            val kontekstId = UUID.randomUUID()

            val ansettelsesforholdUtenforFilter =
                listOf(
                    Ansettelsesforhold(
                        startdato = 3.januar,
                        sluttdato = 13.januar,
                        yrkeskode = "1234567",
                        yrkesbeskrivelse = "BARNEHAGEASSISTENT",
                        stillingsprosent = 100.0,
                    ),
                    Ansettelsesforhold(
                        startdato = 20.januar,
                        sluttdato = 8.februar,
                        yrkeskode = "7654321",
                        yrkesbeskrivelse = "SYKEPLEIER",
                        stillingsprosent = 80.0,
                    ),
                )
            val ansettelsesforholdPerOrgnr =
                mapOf(
                    Mock.orgnr to ansettelsesforholdUtenforFilter,
                )

            testRapid.sendJson(Mock.steg1(kontekstId, ansettelsesforholdPerOrgnr))

            verifySequence {
                mockRedisStore.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success = emptyList<Ansettelsesforhold>().toJson(Ansettelsesforhold.serializer().list()),
                    ),
                )
            }
        }

        test("skriver feilresultat ved mottatt feil") {
            val fail =
                mockFail(
                    feilmelding = "Dra meg baklengs inn i fuglekassa!",
                    eventName = EventName.SERVICE_HENT_ARBEIDSFORHOLD_SELVBESTEMT,
                    kontekstId = UUID.randomUUID(),
                )

            testRapid.sendJson(fail.tilMelding())

            verifySequence {
                mockRedisStore.skrivResultat(
                    fail.kontekstId,
                    ResultJson(
                        failure = fail.feilmelding.toJson(),
                    ),
                )
            }
        }
    }) {
    private object Mock {
        val orgnr = Orgnr.genererGyldig()
        val sykmeldtFnr = Fnr.genererGyldig()
        val fom = 12.februar
        val tom = 6.april

        fun steg0(kontekstId: UUID): Map<Key, JsonElement> =
            mapOf(
                Key.EVENT_NAME to EventName.HENT_ARBEIDSFORHOLD_SELVBESTEMT_REQUESTED.toJson(),
                Key.KONTEKST_ID to kontekstId.toJson(),
                Key.DATA to
                    mapOf(
                        Key.ORGNR_UNDERENHET to orgnr.toJson(Orgnr.serializer()),
                        Key.SYKMELDT_FNR to sykmeldtFnr.toJson(Fnr.serializer()),
                        Key.PERIODE to Periode(fom, tom).toJson(Periode.serializer()),
                    ).toJson(),
            )

        fun steg1(
            kontekstId: UUID,
            ansettelsesforhold: Map<Orgnr, List<Ansettelsesforhold>>,
        ): Map<Key, JsonElement> =
            steg0(kontekstId)
                .plus(Key.EVENT_NAME to EventName.SERVICE_HENT_ARBEIDSFORHOLD_SELVBESTEMT.toJson())
                .plusData(
                    Key.ANSETTELSESFORHOLD to ansettelsesforhold.toJson(MapSerializer(Orgnr.serializer(), Ansettelsesforhold.serializer().list())),
                )
    }
}
