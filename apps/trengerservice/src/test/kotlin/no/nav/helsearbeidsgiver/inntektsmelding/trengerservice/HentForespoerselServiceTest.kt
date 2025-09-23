package no.nav.helsearbeidsgiver.inntektsmelding.trengerservice

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.verify
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import no.nav.hag.simba.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.kontrakt.domene.forespoersel.test.mockForespoersel
import no.nav.hag.simba.kontrakt.resultat.forespoersel.HentForespoerselResultat
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.domene.Person
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.inntektMapSerializer
import no.nav.hag.simba.utils.felles.json.lesOrNull
import no.nav.hag.simba.utils.felles.json.orgMapSerializer
import no.nav.hag.simba.utils.felles.json.personMapSerializer
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.test.json.lesBehov
import no.nav.hag.simba.utils.felles.test.json.lesData
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.service.ServiceRiverStateful
import no.nav.hag.simba.utils.rr.test.message
import no.nav.hag.simba.utils.rr.test.mockConnectToRapid
import no.nav.hag.simba.utils.rr.test.sendJson
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.hag.simba.utils.valkey.test.MockRedis
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.juli
import no.nav.helsearbeidsgiver.utils.test.date.juni
import no.nav.helsearbeidsgiver.utils.test.date.mai
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

class HentForespoerselServiceTest :
    FunSpec({

        val testRapid = TestRapid()
        val mockRedis = MockRedis(RedisPrefix.HentForespoersel)

        mockConnectToRapid(testRapid) {
            listOf(
                ServiceRiverStateful(
                    HentForespoerselService(it, mockRedis.store),
                ),
            )
        }

        beforeEach {
            testRapid.reset()
            clearAllMocks()
            mockRedis.setup()
        }

        test("henter forespørsel og annen data til preutfylling av skjema") {
            val kontekstId = UUID.randomUUID()

            testRapid.sendJson(Mock.steg0(kontekstId))

            testRapid.inspektør.size shouldBeExactly 1
            testRapid.message(0).lesBehov() shouldBe BehovType.HENT_TRENGER_IM

            testRapid.sendJson(Mock.steg1(kontekstId))

            testRapid.inspektør.size shouldBeExactly 4
            testRapid.message(1).also {
                it.lesBehov() shouldBe BehovType.HENT_VIRKSOMHET_NAVN
                Key.SVAR_KAFKA_KEY.lesOrNull(KafkaKey.serializer(), it.lesData()) shouldBe KafkaKey(Mock.forespoerselId)
            }
            testRapid.message(2).also {
                it.lesBehov() shouldBe BehovType.HENT_PERSONER
                Key.SVAR_KAFKA_KEY.lesOrNull(KafkaKey.serializer(), it.lesData()) shouldBe KafkaKey(Mock.forespoerselId)
            }
            testRapid.message(3).also {
                it.lesBehov() shouldBe BehovType.HENT_INNTEKT
                Key.SVAR_KAFKA_KEY.lesOrNull(KafkaKey.serializer(), it.lesData()) shouldBe KafkaKey(Mock.forespoerselId)
            }

            testRapid.sendJson(Mock.steg2(kontekstId))

            testRapid.inspektør.size shouldBeExactly 4

            verify {
                mockRedis.store.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success = Mock.resultat.toJson(HentForespoerselResultat.serializer()),
                    ),
                )
            }
        }

        test("henter forespørsel, men tåler feil for annen data til preutfylling av skjema") {
            val kontekstId = UUID.randomUUID()

            testRapid.sendJson(Mock.steg0(kontekstId))
            testRapid.sendJson(Mock.steg1(kontekstId))
            testRapid.sendJson(Mock.failBehov(kontekstId, BehovType.HENT_VIRKSOMHET_NAVN))
            testRapid.sendJson(Mock.failBehov(kontekstId, BehovType.HENT_PERSONER))
            testRapid.sendJson(Mock.failBehov(kontekstId, BehovType.HENT_INNTEKT))

            testRapid.inspektør.size shouldBeExactly 4

            val tomtMap = JsonObject(emptyMap())

            verify {
                mockRedis.store.skrivMellomlagring(kontekstId, Key.VIRKSOMHETER, tomtMap)
                mockRedis.store.skrivMellomlagring(kontekstId, Key.PERSONER, tomtMap)
                mockRedis.store.skrivMellomlagring(kontekstId, Key.INNTEKT, tomtMap)
                mockRedis.store.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success =
                            Mock.resultat
                                .copy(
                                    sykmeldtNavn = null,
                                    avsenderNavn = null,
                                    orgNavn = null,
                                    inntekt = null,
                                ).toJson(HentForespoerselResultat.serializer()),
                    ),
                )
            }
        }
    })

private object Mock {
    private val sykmeldt =
        Person(
            fnr = Fnr.genererGyldig(),
            navn = "Lange Mann",
        )
    private val avsender =
        Person(
            fnr = Fnr.genererGyldig(),
            navn = "Kaptein Sabeltann",
        )
    private val orgNavn = "Den Sorte Dame"

    val forespoerselId: UUID = UUID.randomUUID()

    val resultat =
        HentForespoerselResultat(
            sykmeldtNavn = sykmeldt.navn,
            avsenderNavn = avsender.navn,
            orgNavn = orgNavn,
            inntekt =
                mapOf(
                    mai(2024) to 36000.0,
                    juni(2024) to 37000.0,
                    juli(2024) to 38000.0,
                ),
            forespoersel =
                mockForespoersel().copy(
                    fnr = sykmeldt.fnr,
                ),
        )

    fun steg0(kontekstId: UUID): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.ARBEIDSGIVER_FNR to avsender.fnr.toJson(),
                ).toJson(),
        )

    fun steg1(kontekstId: UUID): Map<Key, JsonElement> =
        steg0(kontekstId).plus(
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_SVAR to resultat.forespoersel.toJson(Forespoersel.serializer()),
                ).toJson(),
        )

    fun steg2(kontekstId: UUID): Map<Key, JsonElement> =
        steg0(kontekstId).plus(
            Key.DATA to
                mapOf(
                    Key.VIRKSOMHETER to mapOf(resultat.forespoersel.orgnr to orgNavn).toJson(orgMapSerializer),
                    Key.PERSONER to
                        mapOf(
                            sykmeldt.fnr to sykmeldt,
                            avsender.fnr to avsender,
                        ).toJson(personMapSerializer),
                    Key.INNTEKT to resultat.inntekt.shouldNotBeNull().toJson(inntektMapSerializer),
                ).toJson(),
        )

    fun failBehov(
        kontekstId: UUID,
        behovType: BehovType,
    ): Map<Key, JsonElement> =
        Fail(
            feilmelding = "Feil for '$behovType'.",
            kontekstId = kontekstId,
            utloesendeMelding =
                mapOf(
                    Key.EVENT_NAME to EventName.TRENGER_REQUESTED.toJson(),
                    Key.BEHOV to behovType.toJson(),
                    Key.KONTEKST_ID to kontekstId.toJson(),
                ),
        ).tilMelding()
}
