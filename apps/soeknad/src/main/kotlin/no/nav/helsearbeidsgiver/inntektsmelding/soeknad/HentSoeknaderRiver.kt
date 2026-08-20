package no.nav.helsearbeidsgiver.inntektsmelding.soeknad

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.kontrakt.domene.soeknad.Soeknad
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.json.krev
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.inntektsmelding.soeknad.klient.SoeknadKlient
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.LocalDate
import java.util.UUID
import kotlin.collections.orEmpty

data class Melding(
    val eventName: EventName,
    val behovType: BehovType,
    val kontekstId: UUID,
    val data: Map<Key, JsonElement>,
    val svarKafkaKey: KafkaKey,
    val orgnr: Orgnr,
    val fnr: Fnr,
    val eldsteFom: LocalDate,
)

class HentSoeknaderRiver(
    private val soeknadKlient: SoeknadKlient,
) : ObjectRiver.Simba<Melding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): Melding? =
        if (Key.FAIL in json) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()

            Melding(
                eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
                behovType = Key.BEHOV.krev(BehovType.HENT_SOEKNAD_LISTE, BehovType.serializer(), json),
                kontekstId = Key.KONTEKST_ID.les(UuidSerializer, json),
                data = data,
                svarKafkaKey = Key.SVAR_KAFKA_KEY.les(KafkaKey.serializer(), data),
                orgnr = Key.ORGNR_UNDERENHET.les(Orgnr.serializer(), data),
                fnr = Key.SYKMELDT_FNR.les(Fnr.serializer(), data),
                eldsteFom = Key.FRA_OG_MED_DATO.les(LocalDateSerializer, data),
            )
        }

    override fun Melding.bestemNoekkel(): KafkaKey = svarKafkaKey

    override fun Melding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        val soeknader =
            runBlocking {
                soeknadKlient.hentSoeknader(orgnr.verdi, fnr.verdi, eldsteFom)
            }

        return mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                data
                    .plus(
                        Key.SOEKNAD_LISTE to soeknader.toJson(Soeknad.serializer().list()),
                    ).toJson(),
        )
    }

    override fun Melding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke hente søknader fra Flex.",
                kontekstId = kontekstId,
                utloesendeMelding = json,
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun Melding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@HentSoeknaderRiver),
            Log.event(eventName),
            Log.behov(behovType),
            Log.kontekstId(kontekstId),
        )
}
