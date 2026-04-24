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
import no.nav.hag.simba.utils.felles.domene.PeriodeAapen
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
import no.nav.helsearbeidsgiver.utils.json.serializer.set
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

            val ansettelsesperioderUtenforFilter =
                setOf(
                    PeriodeAapen(2.februar, 10.februar),
                    PeriodeAapen(8.april, 30.juni),
                    PeriodeAapen(9.april, null),
                )
            val ansettelsesperioderInnenforFilter =
                setOf(
                    PeriodeAapen(11.februar, 15.mars),
                    PeriodeAapen(10.mars, 1.april),
                    PeriodeAapen(3.april, null),
                    PeriodeAapen(6.april, 16.april),
                    PeriodeAapen(1.januar, 1.juni),
                )
            val ansettelsesperioderPerOrgnr =
                mapOf(
                    Mock.orgnr to ansettelsesperioderUtenforFilter + ansettelsesperioderInnenforFilter,
                    Orgnr.genererGyldig() to
                        setOf(
                            PeriodeAapen(20.mars, 30.mars),
                            PeriodeAapen(1.april, null),
                        ),
                )

            testRapid.sendJson(Mock.steg0(kontekstId))

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.message(0).lesBehov() shouldBe BehovType.HENT_ANSETTELSESPERIODER

            testRapid.sendJson(Mock.steg1(kontekstId, ansettelsesperioderPerOrgnr))

            testRapid.inspektør.size shouldBeExactly 1
            verifySequence {
                mockRedisStore.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success = ansettelsesperioderInnenforFilter.toJson(PeriodeAapen.serializer().set()),
                    ),
                )
            }
        }

        test("tåler ingen ansettelsesperioder funnet") {
            val kontekstId = UUID.randomUUID()

            testRapid.sendJson(Mock.steg1(kontekstId, emptyMap()))

            verifySequence {
                mockRedisStore.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success = emptySet<PeriodeAapen>().toJson(PeriodeAapen.serializer().set()),
                    ),
                )
            }
        }

        test("tåler ingen ansettelsesperioder for gitt orgnr") {
            val kontekstId = UUID.randomUUID()

            val ansettelsesperioderInnenforFilter =
                setOf(
                    PeriodeAapen(10.mars, 1.april),
                    PeriodeAapen(3.april, null),
                )
            val ansettelsesperioderPerOrgnr =
                mapOf(
                    Orgnr.genererGyldig() to ansettelsesperioderInnenforFilter,
                )

            testRapid.sendJson(Mock.steg1(kontekstId, ansettelsesperioderPerOrgnr))

            verifySequence {
                mockRedisStore.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success = emptySet<PeriodeAapen>().toJson(PeriodeAapen.serializer().set()),
                    ),
                )
            }
        }

        test("tåler ingen ansettelsesperioder for gitt periode") {
            val kontekstId = UUID.randomUUID()

            val ansettelsesperioderUtenforFilter =
                setOf(
                    PeriodeAapen(3.januar, 13.januar),
                    PeriodeAapen(20.januar, 8.februar),
                )
            val ansettelsesperioderPerOrgnr =
                mapOf(
                    Mock.orgnr to ansettelsesperioderUtenforFilter,
                )

            testRapid.sendJson(Mock.steg1(kontekstId, ansettelsesperioderPerOrgnr))

            verifySequence {
                mockRedisStore.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success = emptySet<PeriodeAapen>().toJson(PeriodeAapen.serializer().set()),
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
            ansettelsesperioder: Map<Orgnr, Set<PeriodeAapen>>,
        ): Map<Key, JsonElement> =
            steg0(kontekstId)
                .plus(Key.EVENT_NAME to EventName.SERVICE_HENT_ARBEIDSFORHOLD_SELVBESTEMT.toJson())
                .plusData(
                    Key.ANSETTELSESPERIODER to ansettelsesperioder.toJson(MapSerializer(Orgnr.serializer(), PeriodeAapen.serializer().set())),
                )
    }
}
