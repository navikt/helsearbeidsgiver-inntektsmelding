package no.nav.helsearbeidsgiver.inntektsmelding.faisuservice

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.verify
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.kontrakt.domene.forespoersel.test.mockForespoersel
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.PeriodeAapen
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.ansettelsesperioderSerializer
import no.nav.hag.simba.utils.felles.json.lesOrNull
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.test.json.lesBehov
import no.nav.hag.simba.utils.felles.test.json.lesData
import no.nav.hag.simba.utils.felles.test.json.plusData
import no.nav.hag.simba.utils.felles.test.mock.mockFail
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.service.ServiceRiverStateless
import no.nav.hag.simba.utils.rr.test.message
import no.nav.hag.simba.utils.rr.test.mockConnectToRapid
import no.nav.hag.simba.utils.rr.test.sendJson
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.hag.simba.utils.valkey.test.MockRedis
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class HentArbeidsforholdServiceTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockRedis = MockRedis(RedisPrefix.HentArbeidsforhold)

        mockConnectToRapid(testRapid) {
            listOf(
                ServiceRiverStateless(
                    HentArbeidsforholdService(it, mockRedis.store),
                ),
            )
        }

        beforeEach {
            testRapid.reset()
            clearAllMocks()
            mockRedis.setup()
        }

        test("henter og filtrerer ansettelsesperioder for forespoersel") {
            val kontekstId = UUID.randomUUID()
            val gyldigPeriode = PeriodeAapen(fom = 1.januar, tom = 31.januar)
            val ansettelsesperioder =
                mapOf(
                    Mock.forespoersel.orgnr to setOf(gyldigPeriode),
                    Orgnr.genererGyldig() to setOf(PeriodeAapen(fom = 1.januar, tom = 31.januar)),
                )

            testRapid.sendJson(Mock.steg0(kontekstId))

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.message(0).lesBehov() shouldBe BehovType.HENT_TRENGER_IM

            testRapid.sendJson(Mock.steg1(kontekstId))

            testRapid.inspektør.size shouldBeExactly 2
            testRapid.message(1).also {
                it.lesBehov() shouldBe BehovType.HENT_ANSETTELSESPERIODER
                Key.SVAR_KAFKA_KEY.lesOrNull(KafkaKey.serializer(), it.lesData()) shouldBe KafkaKey(Mock.forespoerselId)
            }

            testRapid.sendJson(Mock.steg2(kontekstId, ansettelsesperioder))

            verify {
                mockRedis.store.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success = listOf(gyldigPeriode).toJson(PeriodeAapen.serializer()),
                    ),
                )
            }
        }

        test("filtrerer bort ansettelsesperioder utenfor sykmeldingsperiode") {
            val kontekstId = UUID.randomUUID()
            val periodeInnenfor = PeriodeAapen(fom = 1.januar, tom = 31.januar)
            val periodeUtenfor = PeriodeAapen(fom = 5.februar, tom = 28.februar)
            val ansettelsesperioder =
                mapOf(
                    Mock.forespoersel.orgnr to setOf(periodeInnenfor, periodeUtenfor),
                )

            testRapid.sendJson(Mock.steg0(kontekstId))
            testRapid.sendJson(Mock.steg1(kontekstId))
            testRapid.sendJson(Mock.steg2(kontekstId, ansettelsesperioder))

            verify {
                mockRedis.store.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success = listOf(periodeInnenfor).toJson(PeriodeAapen.serializer()),
                    ),
                )
            }
        }

        test("filtrerer bort ansettelsesperioder for feil orgnr") {
            val kontekstId = UUID.randomUUID()
            val ansettelsesperioder =
                mapOf(
                    Orgnr.genererGyldig() to setOf(PeriodeAapen(fom = 1.januar, tom = 31.januar)),
                )

            testRapid.sendJson(Mock.steg0(kontekstId))
            testRapid.sendJson(Mock.steg1(kontekstId))
            testRapid.sendJson(Mock.steg2(kontekstId, ansettelsesperioder))

            verify {
                mockRedis.store.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success = emptyList<PeriodeAapen>().toJson(PeriodeAapen.serializer()),
                    ),
                )
            }
        }

        test("svarer med feilmelding ved uhåndterbare feil") {
            val fail =
                mockFail(
                    feilmelding = "Klarte ikke hente forespoersel.",
                    eventName = EventName.SERVICE_HENT_ARBEIDSFORHOLD,
                    behovType = BehovType.HENT_TRENGER_IM,
                )

            testRapid.sendJson(Mock.steg0(fail.kontekstId))
            testRapid.sendJson(fail.tilMelding())

            verify {
                mockRedis.store.skrivResultat(
                    fail.kontekstId,
                    ResultJson(failure = fail.feilmelding.toJson()),
                )
            }
        }
    })

private object Mock {
    val forespoerselId: UUID = UUID.randomUUID()
    val forespoersel = mockForespoersel()

    fun steg0(kontekstId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.HENT_ARBEIDSFORHOLD_REQUESTED.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(UuidSerializer),
                ).toJson(),
        )

    fun steg1(kontekstId: UUID): Map<Key, JsonElement> =
        steg0(kontekstId)
            .plus(Key.EVENT_NAME to EventName.SERVICE_HENT_ARBEIDSFORHOLD.toJson())
            .plusData(
                Key.FORESPOERSEL_SVAR to forespoersel.toJson(Forespoersel.serializer()),
            )

    fun steg2(
        kontekstId: UUID,
        ansettelsesperioder: Map<Orgnr, Set<PeriodeAapen>>,
    ): Map<Key, JsonElement> =
        steg1(kontekstId).plusData(
            Key.ANSETTELSESPERIODER to ansettelsesperioder.toJson(ansettelsesperioderSerializer),
        )
}
