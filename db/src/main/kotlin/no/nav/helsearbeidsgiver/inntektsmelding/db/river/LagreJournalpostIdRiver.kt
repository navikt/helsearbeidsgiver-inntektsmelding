package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toPretty
import no.nav.helsearbeidsgiver.felles.loeser.ObjectRiver
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.SelvbestemtImRepo
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding as InntektsmeldingV1

data class LagreJournalpostIdMelding(
    val eventName: EventName,
    val behovType: BehovType,
    val transaksjonId: UUID,
    // TODO erstatt med v1.Inntektsmelding når mulig
    val inntektsmeldingType: InntektsmeldingV1.Type,
    val journalpostId: String,
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
            val forespoerselId = Key.FORESPOERSEL_ID.lesOrNull(UuidSerializer, json)
            val selvbestemtId = Key.SELVBESTEMT_ID.lesOrNull(UuidSerializer, json)

            val inntektsmelding = Key.INNTEKTSMELDING_DOKUMENT.les(Inntektsmelding.serializer(), json)
            val vedtaksperiodeId = inntektsmelding.vedtaksperiodeId

            val inntektsmeldingType =
                if (forespoerselId != null && vedtaksperiodeId != null) {
                    InntektsmeldingV1.Type.Forespurt(
                        id = forespoerselId,
                        vedtaksperiodeId = vedtaksperiodeId,
                    )
                } else if (selvbestemtId != null) {
                    InntektsmeldingV1.Type.Selvbestemt(selvbestemtId)
                } else {
                    null
                }

            if (inntektsmeldingType != null) {
                LagreJournalpostIdMelding(
                    eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
                    behovType = Key.BEHOV.krev(BehovType.LAGRE_JOURNALPOST_ID, BehovType.serializer(), json),
                    transaksjonId = Key.UUID.les(UuidSerializer, json),
                    inntektsmeldingType = inntektsmeldingType,
                    journalpostId = Key.JOURNALPOST_ID.les(String.serializer(), json),
                )
            } else {
                if (Key.BEHOV.lesOrNull(BehovType.serializer(), json) == BehovType.LAGRE_JOURNALPOST_ID) {
                    "Klarte ikke lagre journalpost-ID. Melding mangler inntektsmeldingstype-ID.".also {
                        logger.error(it)
                        sikkerLogger.error("$it\n${json.toPretty()}")
                    }
                }

                null
            }
        }

    override fun LagreJournalpostIdMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        logger.info("Mottok melding.")
        sikkerLogger.info("Mottok melding:\n${json.toPretty()}")

        when (inntektsmeldingType) {
            is InntektsmeldingV1.Type.Forespurt -> {
                imRepo.oppdaterJournalpostId(inntektsmeldingType.id, journalpostId)
            }

            is InntektsmeldingV1.Type.Selvbestemt -> {
                selvbestemtImRepo.oppdaterJournalpostId(inntektsmeldingType.id, journalpostId)
            }
        }

        return mapOf(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_JOURNALFOERT.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.JOURNALPOST_ID to journalpostId.toJson(),
            Key.INNTEKTSMELDING_DOKUMENT to json[Key.INNTEKTSMELDING_DOKUMENT],
            Key.FORESPOERSEL_ID to json[Key.FORESPOERSEL_ID],
            Key.SELVBESTEMT_ID to json[Key.SELVBESTEMT_ID],
        )
            .mapValuesNotNull { it }
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
                forespoerselId = json[Key.FORESPOERSEL_ID]?.fromJson(UuidSerializer),
                utloesendeMelding = json.toJson(),
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
            .plus(Key.SELVBESTEMT_ID to json[Key.SELVBESTEMT_ID])
            .mapValuesNotNull { it }
    }

    override fun LagreJournalpostIdMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@LagreJournalpostIdRiver),
            Log.event(eventName),
            Log.behov(behovType),
            Log.transaksjonId(transaksjonId),
        )
}
