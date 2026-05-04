package no.nav.helsearbeidsgiver.inntektsmelding.aareg

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Ansettelsesforhold
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.domene.PeriodeAapen
import no.nav.hag.simba.utils.felles.json.ansettelsesforholdSerializer
import no.nav.hag.simba.utils.felles.json.ansettelsesperioderSerializer
import no.nav.hag.simba.utils.felles.json.krev
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

data class HentAnsettelsesperioderMelding(
    val eventName: EventName,
    val behovType: BehovType,
    val kontekstId: UUID,
    val data: Map<Key, JsonElement>,
    val svarKafkaKey: KafkaKey,
    val fnr: Fnr,
)

class HentAnsettelsesperioderRiver(
    private val aaregClient: AaregClient,
) : ObjectRiver.Simba<HentAnsettelsesperioderMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): HentAnsettelsesperioderMelding? =
        if (Key.FAIL in json) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()

            HentAnsettelsesperioderMelding(
                eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
                behovType = Key.BEHOV.krev(BehovType.HENT_ANSETTELSESPERIODER, BehovType.serializer(), json),
                kontekstId = Key.KONTEKST_ID.les(UuidSerializer, json),
                data = data,
                svarKafkaKey = Key.SVAR_KAFKA_KEY.les(KafkaKey.serializer(), data),
                fnr = Key.FNR.les(Fnr.serializer(), data),
            )
        }

    override fun HentAnsettelsesperioderMelding.bestemNoekkel(): KafkaKey = svarKafkaKey

    override fun HentAnsettelsesperioderMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        // TODO: Fjern duplikat API-kall mot aareg når ansettelsesperioder ikke lenger brukes
        val ansettelsesforholdFraKlient =
            runBlocking {
                aaregClient.hentAnsettelsesforhold(fnr.verdi, kontekstId.toString())
            }

        sikkerLogger.debug(
            "Hentet ${ansettelsesforholdFraKlient.size} ansettelsesforhold for fnr ${fnr.verdi}. " +
                "Detaljer: $ansettelsesforholdFraKlient",
        )

        val ansettelsesforhold =
            ansettelsesforholdFraKlient.mapValues { (_, forholdListe) ->
                forholdListe
                    .map {
                        Ansettelsesforhold(
                            startdato = it.startdato,
                            sluttdato = it.sluttdato,
                            yrkesKode = it.yrkesKode,
                            yrkesBeskrivelse = it.yrkesBeskrivelse,
                            stillingsprosent = it.stillingsprosent,
                        )
                    }.toSet()
            }

        val ansettelsesperioder =
            runBlocking {
                aaregClient.hentAnsettelsesperioder(fnr.verdi, kontekstId.toString())
            }.mapValues { (_, perioder) ->
                perioder
                    .map {
                        PeriodeAapen(
                            fom = it.fom,
                            tom = it.tom,
                        )
                    }.toSet()
            }

        "Fant ${ansettelsesperioder.size} ansettelsesperioder.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        return mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                data
                    .plus(Key.ANSETTELSESPERIODER to ansettelsesperioder.toJson(ansettelsesperioderSerializer))
                    .plus(Key.ANSETTELSESFORHOLD to ansettelsesforhold.toJson(ansettelsesforholdSerializer))
                    .toJson(),
        )
    }

    override fun HentAnsettelsesperioderMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke hente ansettelsesperioder fra Aareg.",
                kontekstId = kontekstId,
                utloesendeMelding = json,
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun HentAnsettelsesperioderMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@HentAnsettelsesperioderRiver),
            Log.event(eventName),
            Log.behov(behovType),
            Log.kontekstId(kontekstId),
        )
}
