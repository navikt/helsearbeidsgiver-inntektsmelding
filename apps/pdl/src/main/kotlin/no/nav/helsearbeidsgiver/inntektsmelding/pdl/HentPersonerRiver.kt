package no.nav.helsearbeidsgiver.inntektsmelding.pdl

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.pdl.PdlClient
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

data class Melding(
    val eventName: EventName,
    val behovType: BehovType,
    val kontekstId: UUID,
    val data: Map<Key, JsonElement>,
    val svarKafkaKey: KafkaKey,
    val fnrListe: Set<Fnr>,
)

class HentPersonerRiver(
    private val pdlClient: PdlClient,
) : ObjectRiver<Melding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): Melding? =
        if (Key.FAIL in json) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()

            Melding(
                eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
                behovType = Key.BEHOV.krev(BehovType.HENT_PERSONER, BehovType.serializer(), json),
                kontekstId = Key.KONTEKST_ID.les(UuidSerializer, json),
                data = data,
                svarKafkaKey = Key.SVAR_KAFKA_KEY.les(KafkaKey.serializer(), data),
                fnrListe = Key.FNR_LISTE.les(Fnr.serializer().set(), data),
            )
        }

    override fun Melding.bestemNoekkel(): KafkaKey = svarKafkaKey

    override fun Melding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        "Henter navn for ${fnrListe.size} personer.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        val personer = hentPersoner(fnrListe).associateBy { it.fnr }

        if (fnrListe.size != personer.size) {
            "Ba om ${fnrListe.size} personer fra PDL og mottok ${personer.size}.".also {
                logger.warn(it)
                sikkerLogger.warn(it)
            }
        }

        return mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                data
                    .plus(
                        Key.PERSONER to personer.toJson(personMapSerializer),
                    ).toJson(),
        )
    }

    override fun Melding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke hente personer fra PDL.",
                kontekstId = kontekstId,
                utloesendeMelding = json,
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun Melding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@HentPersonerRiver),
            Log.event(eventName),
            Log.behov(behovType),
            Log.kontekstId(kontekstId),
        )

    private fun hentPersoner(fnrListe: Set<Fnr>): List<Person> =
        runBlocking {
            pdlClient.personBolk(
                fnrListe.map(Fnr::verdi),
            )
        }.mapNotNull { person ->
            person.ident?.let { fnr ->
                Person(
                    fnr = Fnr(fnr),
                    navn = person.navn.fulltNavn(),
                )
            }
        }
}
