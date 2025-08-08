package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toPretty
import no.nav.helsearbeidsgiver.felles.model.Fail
import no.nav.helsearbeidsgiver.felles.rr.KafkaKey
import no.nav.helsearbeidsgiver.felles.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.SelvbestemtImRepo
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class LagreJournalpostIdMelding(
    val eventName: EventName,
    val kontekstId: UUID,
    val inntektsmelding: Inntektsmelding,
    val journalpostId: String,
)

class LagreJournalpostIdRiver(
    private val imRepo: InntektsmeldingRepository,
    private val selvbestemtImRepo: SelvbestemtImRepo,
) : ObjectRiver.Simba<LagreJournalpostIdMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): LagreJournalpostIdMelding? =
        if (setOf(Key.BEHOV, Key.DATA, Key.FAIL).any(json::containsKey)) {
            null
        } else {
            LagreJournalpostIdMelding(
                eventName = Key.EVENT_NAME.krev(EventName.INNTEKTSMELDING_JOURNALFOERT, EventName.serializer(), json),
                kontekstId = Key.KONTEKST_ID.les(UuidSerializer, json),
                inntektsmelding = Key.INNTEKTSMELDING.les(Inntektsmelding.serializer(), json),
                journalpostId = Key.JOURNALPOST_ID.les(String.serializer(), json),
            )
        }

    override fun LagreJournalpostIdMelding.bestemNoekkel(): KafkaKey = KafkaKey(inntektsmelding.type.id)

    override fun LagreJournalpostIdMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement>? {
        logger.info("Mottok melding.")
        sikkerLogger.info("Mottok melding:\n${json.toPretty()}")

        when (inntektsmelding.type) {
            is Inntektsmelding.Type.Forespurt, is Inntektsmelding.Type.ForespurtEkstern -> {
                imRepo.oppdaterJournalpostId(inntektsmelding.id, journalpostId)

                if (imRepo.hentNyesteBerikedeInntektsmeldingId(inntektsmelding.type.id) != inntektsmelding.id) {
                    "Inntektsmelding journalført, men ikke distribuert pga. nyere innsending.".also {
                        logger.info(it)
                        sikkerLogger.info(it)
                    }
                    return null
                }
            }

            is Inntektsmelding.Type.Selvbestemt, is Inntektsmelding.Type.Fisker, is Inntektsmelding.Type.UtenArbeidsforhold -> {
                selvbestemtImRepo.oppdaterJournalpostId(inntektsmelding.id, journalpostId)
            }
        }

        return mapOf(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_JOURNALPOST_ID_LAGRET.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
            Key.JOURNALPOST_ID to journalpostId.toJson(),
        ).also {
            logger.info("Publiserer event '${EventName.INNTEKTSMELDING_JOURNALPOST_ID_LAGRET}' med journalpost-ID '$journalpostId'.")
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
                kontekstId = kontekstId,
                utloesendeMelding = json,
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun LagreJournalpostIdMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@LagreJournalpostIdRiver),
            Log.event(eventName),
            Log.kontekstId(kontekstId),
            Log.inntektsmeldingId(inntektsmelding.id),
            when (inntektsmelding.type) {
                is Inntektsmelding.Type.Forespurt, is Inntektsmelding.Type.ForespurtEkstern -> Log.forespoerselId(inntektsmelding.type.id)
                is Inntektsmelding.Type.Selvbestemt, is Inntektsmelding.Type.Fisker, is Inntektsmelding.Type.UtenArbeidsforhold ->
                    Log.selvbestemtId(
                        inntektsmelding.type.id,
                    )
            },
        )
}
