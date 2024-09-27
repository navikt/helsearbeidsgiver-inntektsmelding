package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toPretty
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.SelvbestemtImRepo
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class LagreJournalpostIdMelding(
    val eventName: EventName,
    val behovType: BehovType,
    val transaksjonId: UUID,
    val inntektsmelding: Inntektsmelding,
    val journalpostId: String,
    val innsendingId: Long?,
)

class LagreJournalpostIdRiver(
    private val imRepo: InntektsmeldingRepository,
    private val selvbestemtImRepo: SelvbestemtImRepo,
) : ObjectRiver<LagreJournalpostIdMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): LagreJournalpostIdMelding? =
        if (setOf(Key.DATA, Key.FAIL).any(json::containsKey)) {
            null
        } else {
            LagreJournalpostIdMelding(
                eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
                behovType = Key.BEHOV.krev(BehovType.LAGRE_JOURNALPOST_ID, BehovType.serializer(), json),
                transaksjonId = Key.UUID.les(UuidSerializer, json),
                inntektsmelding = Key.INNTEKTSMELDING.les(Inntektsmelding.serializer(), json),
                journalpostId = Key.JOURNALPOST_ID.les(String.serializer(), json),
                innsendingId = Key.INNSENDING_ID.lesOrNull(Long.serializer(), json),
            )
        }

    override fun LagreJournalpostIdMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement>? {
        logger.info("Mottok melding.")
        sikkerLogger.info("Mottok melding:\n${json.toPretty()}")

        when (inntektsmelding.type) {
            is Inntektsmelding.Type.Forespurt -> {
                if (innsendingId != null) {
                    imRepo.oppdaterJournalpostId(innsendingId, journalpostId)

                    if (imRepo.hentNyesteBerikedeInnsendingId(inntektsmelding.type.id) != innsendingId) {
                        "Inntektsmelding journalført, men ikke distribuert pga. nyere innsending.".also {
                            logger.info(it)
                            sikkerLogger.info(it)
                        }
                        return null
                    }
                } else {
                    "Klarte ikke journalføre pga. manglende innsending-ID for forespørsel '${inntektsmelding.type.id}' og journalpost-ID '$journalpostId'."
                        .also {
                            logger.info(it)
                            sikkerLogger.info(it)
                        }
                }
            }

            is Inntektsmelding.Type.Selvbestemt -> {
                selvbestemtImRepo.oppdaterJournalpostId(inntektsmelding.id, journalpostId)
            }
        }

        return mapOf(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_JOURNALFOERT.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
            Key.BESTEMMENDE_FRAVAERSDAG to json[Key.BESTEMMENDE_FRAVAERSDAG],
            Key.JOURNALPOST_ID to journalpostId.toJson(),
        ).mapValuesNotNull { it }
            .also {
                logger.info("Publiserer event '${EventName.INNTEKTSMELDING_JOURNALFOERT}' med journalpost-ID '$journalpostId'.")
                sikkerLogger.info("Publiserer event:\n${it.toPretty()}")
            }
    }

    override fun LagreJournalpostIdMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke lagre journalpost-ID '$journalpostId'.",
                event = eventName,
                transaksjonId = transaksjonId,
                forespoerselId = null,
                utloesendeMelding = json.toJson(),
            )

        logger.error(fail.feilmelding, error) // TODO temp error
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun LagreJournalpostIdMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@LagreJournalpostIdRiver),
            Log.event(eventName),
            Log.behov(behovType),
            Log.transaksjonId(transaksjonId),
            when (inntektsmelding.type) {
                is Inntektsmelding.Type.Forespurt -> Log.forespoerselId(inntektsmelding.type.id)
                is Inntektsmelding.Type.Selvbestemt -> Log.selvbestemtId(inntektsmelding.type.id)
            },
        )
}
