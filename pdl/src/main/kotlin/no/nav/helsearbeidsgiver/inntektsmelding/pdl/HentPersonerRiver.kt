package no.nav.helsearbeidsgiver.inntektsmelding.pdl

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Person
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.loeser.ObjectRiver
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.metrics.recordTime
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.pdl.PdlClient
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

data class Melding(
    val eventName: EventName,
    val behovType: BehovType,
    val transaksjonId: UUID,
    val fnrListe: List<Fnr>,
)

class HentPersonerRiver(
    private val pdlClient: PdlClient,
) : ObjectRiver<Melding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): Melding? =
        if (setOf(Key.DATA, Key.FAIL).any(json::containsKey)) {
            null
        } else {
            Melding(
                eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
                behovType = Key.BEHOV.krev(BehovType.HENT_PERSONER, BehovType.serializer(), json),
                transaksjonId = Key.UUID.les(UuidSerializer, json),
                fnrListe = Key.FNR_LISTE.les(Fnr.serializer().list(), json),
            )
        }

    override fun Melding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        "Henter navn for ${fnrListe.size} personer.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        val personer = hentPersoner(fnrListe).associateBy { it.fnr }

        "Ba om ${fnrListe.size} personer fra PDL og mottok ${personer.size}.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        val dataMap =
            mapOf(
                Key.FORESPOERSEL_ID to json[Key.FORESPOERSEL_ID],
                Key.SELVBESTEMT_ID to json[Key.SELVBESTEMT_ID],
                Key.PERSONER to personer.toJson(personMapSerializer),
            )
                .mapValuesNotNull { it }

        return mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to dataMap.toJson(),
            *dataMap.toList().toTypedArray(),
        )
    }

    override fun Melding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke hente personer fra PDL.",
                event = eventName,
                transaksjonId = transaksjonId,
                forespoerselId = json[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer),
                utloesendeMelding = json.toJson(),
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
            .plus(Key.SELVBESTEMT_ID to json[Key.SELVBESTEMT_ID])
            .mapValuesNotNull { it }
    }

    override fun Melding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@HentPersonerRiver),
            Log.event(eventName),
            Log.behov(behovType),
            Log.transaksjonId(transaksjonId),
        )

    private fun hentPersoner(fnrListe: List<Fnr>): List<Person> =
        Metrics.pdlRequest.recordTime(pdlClient::personBolk) {
            pdlClient.personBolk(
                fnrListe.map(Fnr::verdi),
            )
        }
            .orEmpty()
            .mapNotNull { person ->
                person.ident?.let { fnr ->
                    Person(
                        fnr = Fnr(fnr),
                        navn = person.navn.fulltNavn(),
                        foedselsdato = person.foedselsdato,
                    )
                }
            }
}
