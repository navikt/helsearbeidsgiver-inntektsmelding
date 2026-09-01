package no.nav.helsearbeidsgiver.inntektsmelding.soeknadservice

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.kontrakt.domene.soeknad.Soeknad
import no.nav.hag.simba.kontrakt.resultat.soeknad.hentSoeknaderResultatSerializer
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.Tekst
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.Publisher
import no.nav.hag.simba.utils.rr.service.ServiceMed2Steg
import no.nav.hag.simba.utils.valkey.RedisStore
import no.nav.hag.simba.utils.valkey.ResultJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.LocalDate
import java.util.UUID
import kotlin.collections.plus

data class Steg0(
    val kontekstId: UUID,
    val orgnr: Orgnr,
    val sykmeldtFnr: Fnr,
    val erBehandlingsdager: Boolean,
)

data class Steg1(
    val soeknader: List<Soeknad>,
)

data class Steg2(
    val forespoersler: Map<UUID, Forespoersel>,
)

class HentSoeknaderService(
    private val publisher: Publisher,
    private val redisStore: RedisStore,
) : ServiceMed2Steg<Steg0, Steg1, Steg2>() {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val initialEventName = EventName.REQUEST_HENT_SOEKNAD_LISTE
    override val serviceEventName = EventName.SERVICE_HENT_SOEKNAD_LISTE

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            kontekstId = Key.KONTEKST_ID.les(UuidSerializer, melding),
            orgnr = Key.ORGNR_UNDERENHET.les(Orgnr.serializer(), melding),
            sykmeldtFnr = Key.SYKMELDT_FNR.les(Fnr.serializer(), melding),
            erBehandlingsdager = Key.ER_BEHANDLINGSDAGER.les(Boolean.serializer(), melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            soeknader = Key.SOEKNAD_LISTE.les(Soeknad.serializer().list(), melding),
        )

    override fun lesSteg2(melding: Map<Key, JsonElement>): Steg2 =
        Steg2(
            forespoersler = Key.FORESPOERSEL_MAP.les(MapSerializer(UuidSerializer, Forespoersel.serializer()), melding),
        )

    override fun utfoerSteg0(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
    ) {
        val hentSoeknaderFraOgMed = LocalDate.now().minusYears(3)

        publisher
            .publish(
                key = steg0.sykmeldtFnr,
                Key.EVENT_NAME to serviceEventName.toJson(),
                Key.BEHOV to BehovType.HENT_SOEKNAD_LISTE.toJson(),
                Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                Key.DATA to
                    data
                        .plus(
                            mapOf(
                                Key.SVAR_KAFKA_KEY to KafkaKey(steg0.sykmeldtFnr).toJson(),
                                Key.ORGNR_UNDERENHET to steg0.orgnr.toJson(),
                                Key.SYKMELDT_FNR to steg0.sykmeldtFnr.toJson(),
                                Key.FRA_OG_MED_DATO to hentSoeknaderFraOgMed.toJson(),
                            ),
                        ).toJson(),
            ).also { loggBehovPublisert(BehovType.HENT_SOEKNAD_LISTE, it) }
    }

    override fun utfoerSteg1(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
    ) {
        if (steg0.erBehandlingsdager) {
            utfoerSteg2(
                data = data,
                steg0 = steg0,
                steg1 = steg1,
                steg2 = Steg2(forespoersler = emptyMap()),
            )
        } else {
            val vedtaksperiodeIder = steg1.soeknader.filterIsInstance<Soeknad.Arbeidstaker>().map { it.vedtaksperiodeId }

            publisher
                .publish(
                    key = steg0.sykmeldtFnr,
                    Key.EVENT_NAME to serviceEventName.toJson(),
                    Key.BEHOV to BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE.toJson(),
                    Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                    Key.DATA to
                        data
                            .plus(
                                mapOf(
                                    Key.SVAR_KAFKA_KEY to KafkaKey(steg0.sykmeldtFnr).toJson(),
                                    Key.VEDTAKSPERIODE_ID_LISTE to vedtaksperiodeIder.toJson(UuidSerializer),
                                ),
                            ).toJson(),
                ).also { loggBehovPublisert(BehovType.HENT_FORESPOERSLER_FOR_VEDTAKSPERIODE_ID_LISTE, it) }
        }
    }

    override fun utfoerSteg2(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
    ) {
        val result = tilKategorier(steg0.erBehandlingsdager, steg1.soeknader, steg2.forespoersler)
        val resultJson =
            ResultJson(
                success = result.toJson(hentSoeknaderResultatSerializer),
            )

        redisStore.skrivResultat(steg0.kontekstId, resultJson)
    }

    override fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    ) {
        "Uoverkommelig feil oppsto under henting av søknader.".also {
            logger.warn(it)
            sikkerLogger.warn(it)
        }

        val resultJson = ResultJson(failure = Tekst.TEKNISK_FEIL_FORBIGAAENDE.toJson())

        redisStore.skrivResultat(fail.kontekstId, resultJson)
    }

    override fun Steg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@HentSoeknaderService),
            Log.event(serviceEventName),
            Log.kontekstId(kontekstId),
        )

    private fun loggBehovPublisert(
        behovType: BehovType,
        publisert: JsonElement,
    ) {
        MdcUtils.withLogFields(
            Log.behov(behovType),
        ) {
            "Publiserte melding med behov $behovType.".let {
                logger.info(it)
                sikkerLogger.info("$it\n${publisert.toPretty()}")
            }
        }
    }
}

private fun tilKategorier(
    erBehandlingsdager: Boolean,
    soeknader: List<Soeknad>,
    forespoersler: Map<UUID, Forespoersel>,
): Triple<List<Pair<UUID, Forespoersel>>, List<Soeknad.Arbeidstaker>, List<Soeknad.Behandlingsdager>> =
    if (!erBehandlingsdager) {
        val forespoerselPerVid = forespoersler.toList().associateBy { it.second.vedtaksperiodeId }

        soeknader
            .filterIsInstance<Soeknad.Arbeidstaker>()
            .fold(
                Triple(emptyList(), emptyList(), emptyList()),
            ) { delresultat, soeknad ->
                val forespoerselMedId = forespoerselPerVid[soeknad.vedtaksperiodeId]
                if (forespoerselMedId != null) {
                    delresultat.copy(
                        first = delresultat.first.plus(forespoerselMedId),
                    )
                } else {
                    delresultat.copy(
                        second = delresultat.second.plus(soeknad),
                    )
                }
            }
    } else {
        Triple(
            emptyList(),
            emptyList(),
            soeknader.filterIsInstance<Soeknad.Behandlingsdager>(),
        )
    }
