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
import no.nav.hag.simba.kontrakt.domene.soeknad.Soeknad
import no.nav.hag.simba.kontrakt.domene.soeknad.test.mockSoeknadArbeidstaker
import no.nav.hag.simba.kontrakt.domene.soeknad.test.mockSoeknadBehandlingsdager
import no.nav.hag.simba.kontrakt.resultat.soeknad.ForespoerselMedId
import no.nav.hag.simba.kontrakt.resultat.soeknad.SoeknadMedForlengerId
import no.nav.hag.simba.kontrakt.resultat.soeknad.hentSoeknaderResultatSerializer
import no.nav.hag.simba.kontrakt.resultat.soeknad.test.mockForespoerselMedId
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
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.juli
import no.nav.helsearbeidsgiver.utils.test.date.juni
import no.nav.helsearbeidsgiver.utils.test.date.mai
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.date.oktober
import no.nav.helsearbeidsgiver.utils.test.date.september
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
                                    Mock.forespoerselMedId3,
                                ),
                                listOf(
                                    Mock.soeknadUtenForespoersel1,
                                    Mock.soeknadUtenForespoersel2,
                                    Mock.soeknadUtenForespoersel3,
                                    Mock.soeknadUtenForespoersel4,
                                    Mock.soeknadUtenForespoersel5,
                                    Mock.soeknadUtenForespoersel6,
                                    Mock.soeknadUtenForespoersel7,
                                    Mock.soeknadUtenForespoersel8,
                                    Mock.soeknadUtenForespoersel9,
                                ),
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
                                emptyList<ForespoerselMedId>(),
                                emptyList<SoeknadMedForlengerId>(),
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
                                emptyList<ForespoerselMedId>(),
                                emptyList<SoeknadMedForlengerId>(),
                                emptyList<Soeknad.Behandlingsdager>(),
                            ).toJson(hentSoeknaderResultatSerializer),
                    ),
                )
            }
        }

        test("svarer med feil dersom noe går galt") {
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
    private val soeknadMedForespoersel1 =
        mockSoeknadArbeidstaker().copy(
            sykmeldingsperiode = 8.mai til 12.mai,
            egenmeldingerFraSykmelding = emptyList(),
            erGradert = true,
        )

    // forlenges
    private val soeknadMedForespoersel2 =
        mockSoeknadArbeidstaker().copy(
            sykmeldingsperiode = 18.mai til 31.mai,
            egenmeldingerFraSykmelding = listOf(15.mai til 17.mai),
        )
    private val soeknadMedForespoersel3 =
        mockSoeknadArbeidstaker().copy(
            sykmeldingsperiode = 7.august til 28.august,
            egenmeldingerFraSykmelding = listOf(4.august til 6.august),
        )
    val forespoerselMedId1 = mockForespoerselMedId(soeknadMedForespoersel1)
    val forespoerselMedId2 = mockForespoerselMedId(soeknadMedForespoersel2)
    val forespoerselMedId3 = mockForespoerselMedId(soeknadMedForespoersel3)
    val soeknadUtenForespoersel1 =
        SoeknadMedForlengerId(
            soeknad =
                mockSoeknadArbeidstaker().copy(
                    sykmeldingsperiode = 5.januar til 30.januar,
                    egenmeldingerFraSykmelding = listOf(2.januar til 3.januar),
                ),
            forlengerVedtaksperiodeId = null,
        )

    // forlenges
    val soeknadUtenForespoersel2 =
        SoeknadMedForlengerId(
            soeknad =
                mockSoeknadArbeidstaker().copy(
                    sykmeldingsperiode = 7.februar til 28.februar,
                    egenmeldingerFraSykmelding = listOf(6.februar til 6.februar),
                ),
            forlengerVedtaksperiodeId = null,
        )

    // forlenger forrige
    val soeknadUtenForespoersel3 =
        SoeknadMedForlengerId(
            soeknad =
                mockSoeknadArbeidstaker().copy(
                    sykmeldingsperiode = 1.mars til 17.mars,
                    egenmeldingerFraSykmelding = emptyList(),
                    erGradert = true,
                ),
            forlengerVedtaksperiodeId = soeknadUtenForespoersel2.soeknad.vedtaksperiodeId,
        )

    // forlenger forrige
    val soeknadUtenForespoersel4 =
        SoeknadMedForlengerId(
            soeknad =
                mockSoeknadArbeidstaker().copy(
                    sykmeldingsperiode = 18.mars til 2.april,
                    egenmeldingerFraSykmelding = emptyList(),
                ),
            forlengerVedtaksperiodeId = soeknadUtenForespoersel3.forlengerVedtaksperiodeId,
        )

    // bryter forlengelse
    val soeknadUtenForespoersel5 =
        SoeknadMedForlengerId(
            soeknad =
                mockSoeknadArbeidstaker().copy(
                    sykmeldingsperiode = 4.april til 24.april,
                    egenmeldingerFraSykmelding = emptyList(),
                ),
            forlengerVedtaksperiodeId = null,
        )

    // forlenger forespoersel
    val soeknadUtenForespoersel6 =
        SoeknadMedForlengerId(
            soeknad =
                mockSoeknadArbeidstaker().copy(
                    sykmeldingsperiode = 1.juni til 10.juni,
                    egenmeldingerFraSykmelding = emptyList(),
                ),
            forlengerVedtaksperiodeId = forespoerselMedId2.forespoersel.vedtaksperiodeId,
        )

    // bryter forlengelse forespoersel
    val soeknadUtenForespoersel7 =
        SoeknadMedForlengerId(
            soeknad =
                mockSoeknadArbeidstaker().copy(
                    sykmeldingsperiode = 12.juni til 20.juni,
                    egenmeldingerFraSykmelding = emptyList(),
                ),
            forlengerVedtaksperiodeId = null,
        )

    // forlenges ikke av egenmeldinger
    val soeknadUtenForespoersel8 =
        SoeknadMedForlengerId(
            soeknad =
                mockSoeknadArbeidstaker().copy(
                    sykmeldingsperiode = 22.juni til 29.juni,
                    egenmeldingerFraSykmelding = listOf(21.juni til 21.juni),
                ),
            forlengerVedtaksperiodeId = null,
        )
    val soeknadUtenForespoersel9 =
        SoeknadMedForlengerId(
            soeknad =
                mockSoeknadArbeidstaker().copy(
                    sykmeldingsperiode = 10.oktober til 20.oktober,
                    egenmeldingerFraSykmelding = listOf(7.oktober til 7.oktober),
                ),
            forlengerVedtaksperiodeId = null,
        )
    val soeknadBehandlingsdager1 =
        mockSoeknadBehandlingsdager().copy(
            sykmeldingsperiode = 9.juli til 29.juli,
            behandlingsdager =
                setOf(
                    10.juli,
                    17.juli,
                    24.juli,
                ),
        )
    val soeknadBehandlingsdager2 =
        mockSoeknadBehandlingsdager().copy(
            sykmeldingsperiode = 3.september til 19.september,
            behandlingsdager =
                setOf(
                    3.september,
                    10.september,
                ),
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
                        soeknadUtenForespoersel1.soeknad,
                        soeknadUtenForespoersel2.soeknad,
                        soeknadUtenForespoersel3.soeknad,
                        soeknadUtenForespoersel4.soeknad,
                        soeknadUtenForespoersel5.soeknad,
                        soeknadMedForespoersel1,
                        soeknadMedForespoersel2,
                        soeknadUtenForespoersel6.soeknad,
                        soeknadUtenForespoersel7.soeknad,
                        soeknadUtenForespoersel8.soeknad,
                        soeknadBehandlingsdager1,
                        soeknadMedForespoersel3,
                        soeknadBehandlingsdager2,
                        soeknadUtenForespoersel9.soeknad,
                    ).toJson(Soeknad.serializer().list()),
            )

    fun steg2(kontekstId: UUID): Map<Key, JsonElement> =
        steg1(kontekstId, false).plusData(
            Key.FORESPOERSEL_MAP to
                listOf(
                    forespoerselMedId1,
                    forespoerselMedId2,
                    forespoerselMedId3,
                ).associate { it.forespoerselId to it.forespoersel }
                    .toJson(MapSerializer(UuidSerializer, Forespoersel.serializer())),
        )
}
