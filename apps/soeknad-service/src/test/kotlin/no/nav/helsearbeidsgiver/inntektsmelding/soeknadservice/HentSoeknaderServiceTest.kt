package no.nav.helsearbeidsgiver.inntektsmelding.soeknadservice

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verifySequence
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import no.nav.hag.simba.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.kontrakt.domene.forespoersel.test.mockForespoersel
import no.nav.hag.simba.kontrakt.domene.soeknad.Soeknad
import no.nav.hag.simba.kontrakt.domene.soeknad.test.mockSoeknadArbeidstaker
import no.nav.hag.simba.kontrakt.domene.soeknad.test.mockSoeknadBehandlingsdager
import no.nav.hag.simba.kontrakt.resultat.soeknad.hentSoeknaderResultatSerializer
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.Tekst
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
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.hag.simba.utils.valkey.ResultJson
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.desember
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.LocalDate
import java.util.UUID

class HentSoeknaderServiceTest :
    FunSpec({
        val testRapid = TestRapid()
        val mockRedisStore = mockk<RedisStore>(relaxed = true)

        mockConnectToRapid(testRapid) {
            listOf(
                ServiceRiverStateless(
                    HentSoeknaderService(it, mockRedisStore),
                ),
            )
        }

        beforeEach {
            testRapid.reset()
            clearAllMocks()
        }

        test("henter søknader _uten_ behandlingsdager") {
            val kontekstId = UUID.randomUUID()
            val erBehandlingsdager = false

            testRapid.sendJson(Mock.steg0(kontekstId, erBehandlingsdager))

            // Melding med forventet behov og data for å hente søknader
            testRapid.inspektør.size shouldBeExactly 1
            testRapid.message(0).also {
                it.lesBehov() shouldBe BehovType.HENT_SOEKNAD_LISTE

                val data = it.lesData()
                Key.SVAR_KAFKA_KEY.lesOrNull(KafkaKey.serializer(), data).shouldNotBeNull()
                Key.ORGNR_UNDERENHET.lesOrNull(Orgnr.serializer(), data).shouldNotBeNull()
                Key.SYKMELDT_FNR.lesOrNull(Fnr.serializer(), data).shouldNotBeNull()
                Key.FRA_OG_MED_DATO.lesOrNull(LocalDateSerializer, data) shouldBe LocalDate.now().minusYears(3)
            }

            testRapid.sendJson(Mock.steg1(kontekstId, erBehandlingsdager))

            // Melding med forventet behov og data for å hente forespørsler
            testRapid.inspektør.size shouldBeExactly 2
            testRapid.message(1).also {
                it.lesBehov() shouldBe BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE

                val data = it.lesData()
                Key.SVAR_KAFKA_KEY.lesOrNull(KafkaKey.serializer(), data).shouldNotBeNull()
                Key.VEDTAKSPERIODE_ID_LISTE.lesOrNull(UuidSerializer.list(), data).shouldNotBeNull()
            }

            testRapid.sendJson(Mock.steg2(kontekstId))

            testRapid.inspektør.size shouldBeExactly 2

            verifySequence {
                mockRedisStore.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success =
                            Triple(
                                listOf(
                                    Mock.forespoerselMedId1,
                                    Mock.forespoerselMedId2,
                                ),
                                listOf(Mock.soeknadUtenForespoersel),
                                emptyList<Soeknad.Behandlingsdager>(),
                            ).toJson(hentSoeknaderResultatSerializer),
                    ),
                )
            }
        }

        test("henter søknader _med_ behandlingsdager") {
            val kontekstId = UUID.randomUUID()
            val erBehandlingsdager = true

            testRapid.sendJson(Mock.steg0(kontekstId, erBehandlingsdager))

            // Melding med forventet behov og data for å hente søknader
            testRapid.inspektør.size shouldBeExactly 1
            testRapid.message(0).also {
                it.lesBehov() shouldBe BehovType.HENT_SOEKNAD_LISTE

                val data = it.lesData()
                Key.SVAR_KAFKA_KEY.lesOrNull(KafkaKey.serializer(), data).shouldNotBeNull()
                Key.ORGNR_UNDERENHET.lesOrNull(Orgnr.serializer(), data).shouldNotBeNull()
                Key.SYKMELDT_FNR.lesOrNull(Fnr.serializer(), data).shouldNotBeNull()
                Key.FRA_OG_MED_DATO.lesOrNull(LocalDateSerializer, data) shouldBe LocalDate.now().minusYears(3)
            }

            testRapid.sendJson(Mock.steg1(kontekstId, erBehandlingsdager))

            // Hopper over melding for å hente forespørsler
            testRapid.inspektør.size shouldBeExactly 1

            verifySequence {
                mockRedisStore.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success =
                            Triple(
                                emptyList<Pair<UUID, Forespoersel>>(),
                                emptyList<Soeknad.Arbeidstaker>(),
                                listOf(
                                    Mock.soeknadBehandlingsdager1,
                                    Mock.soeknadBehandlingsdager2,
                                ),
                            ).toJson(hentSoeknaderResultatSerializer),
                    ),
                )
            }
        }

        test("ingen søknader funnet") {
            val kontekstId = UUID.randomUUID()

            testRapid.sendJson(
                Mock.steg2(kontekstId).plusData(
                    mapOf(
                        Key.SOEKNAD_LISTE to JsonArray(emptyList()),
                        Key.FORESPOERSEL_MAP to JsonObject(emptyMap()),
                    ),
                ),
            )

            testRapid.inspektør.size shouldBeExactly 0

            verifySequence {
                mockRedisStore.skrivResultat(
                    kontekstId,
                    ResultJson(
                        success =
                            Triple(
                                emptyList<Pair<UUID, Forespoersel>>(),
                                emptyList<Soeknad.Arbeidstaker>(),
                                emptyList<Soeknad.Behandlingsdager>(),
                            ).toJson(hentSoeknaderResultatSerializer),
                    ),
                )
            }
        }

        test("svarer med med feil dersom noe går galt") {
            val fail =
                mockFail(
                    feilmelding = "Tonight the streets are red, the lights are blue and blinding",
                    eventName = EventName.SERVICE_HENT_SOEKNAD_LISTE,
                    behovType = BehovType.HENT_SOEKNAD_LISTE,
                )

            testRapid.sendJson(fail.tilMelding())

            testRapid.inspektør.size shouldBeExactly 0

            verifySequence {
                mockRedisStore.skrivResultat(
                    fail.kontekstId,
                    ResultJson(
                        failure = Tekst.TEKNISK_FEIL_FORBIGAAENDE.toJson(),
                    ),
                )
            }
        }
    })

private object Mock {
    val forespoerselMedId1 = UUID.randomUUID() to mockForespoersel()
    val forespoerselMedId2 = UUID.randomUUID() to mockForespoersel()

    val soeknadUtenForespoersel = mockSoeknadArbeidstaker()
    val soeknadBehandlingsdager1 = mockSoeknadBehandlingsdager()
    val soeknadBehandlingsdager2 =
        mockSoeknadBehandlingsdager().copy(
            sykmeldingsperiode = 9.januar(2019) til 29.januar(2019),
            behandlingsdager =
                setOf(
                    10.januar(2019),
                    17.januar(2019),
                    24.januar(2019),
                ),
        )
    private val soeknadMedForespoersel1 =
        mockSoeknadArbeidstaker().copy(
            vedtaksperiodeId = forespoerselMedId1.second.vedtaksperiodeId,
            sykmeldingsperiode = 13.desember til 20.desember,
            egenmeldingerFraSykmelding = emptyList(),
            erGradert = true,
        )
    private val soeknadMedForespoersel2 =
        mockSoeknadArbeidstaker().copy(
            vedtaksperiodeId = forespoerselMedId2.second.vedtaksperiodeId,
            sykmeldingsperiode = 7.februar(2019) til 28.februar(2019),
            egenmeldingerFraSykmelding = listOf(4.februar(2019) til 6.februar(2019)),
        )

    fun steg0(
        kontekstId: UUID,
        erBehandlingsdager: Boolean,
    ): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to EventName.REQUEST_HENT_SOEKNAD_LISTE.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.ORGNR_UNDERENHET to Orgnr.genererGyldig().toJson(),
                    Key.SYKMELDT_FNR to Fnr.genererGyldig().toJson(),
                    Key.ER_BEHANDLINGSDAGER to erBehandlingsdager.toJson(Boolean.serializer()),
                ).toJson(),
        )

    fun steg1(
        kontekstId: UUID,
        erBehandlingsdager: Boolean,
    ): Map<Key, JsonElement> =
        steg0(kontekstId, erBehandlingsdager)
            .plus(Key.EVENT_NAME to EventName.SERVICE_HENT_SOEKNAD_LISTE.toJson())
            .plusData(
                Key.SOEKNAD_LISTE to
                    listOf(
                        soeknadUtenForespoersel,
                        soeknadBehandlingsdager1,
                        soeknadMedForespoersel1,
                        soeknadBehandlingsdager2,
                        soeknadMedForespoersel2,
                    ).toJson(Soeknad.serializer().list()),
            )

    fun steg2(kontekstId: UUID): Map<Key, JsonElement> =
        steg1(kontekstId, false).plusData(
            Key.FORESPOERSEL_MAP to
                mapOf(
                    forespoerselMedId1,
                    forespoerselMedId2,
                ).toJson(MapSerializer(UuidSerializer, Forespoersel.serializer())),
        )
}
