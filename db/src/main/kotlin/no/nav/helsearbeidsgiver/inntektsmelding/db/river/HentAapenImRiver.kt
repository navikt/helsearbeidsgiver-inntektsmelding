package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.loeser.ObjectRiver
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.db.AapenImRepo
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class HentAapenImMelding(
    val eventName: EventName,
    val behovType: BehovType,
    val transaksjonId: UUID,
    val aapenId: UUID
)

// TODO test
class HentAapenImRiver(
    private val aapenImRepo: AapenImRepo
) : ObjectRiver<HentAapenImMelding>() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): HentAapenImMelding {
        if (setOf(Key.DATA, Key.FAIL).any(json::containsKey)) {
            throw AvvisMeldingException()
        }

        return HentAapenImMelding(
            eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
            behovType = Key.BEHOV.krev(BehovType.HENT_AAPEN_IM, BehovType.serializer(), json),
            transaksjonId = Key.UUID.les(UuidSerializer, json),
            aapenId = Key.AAPEN_ID.les(UuidSerializer, json)
        )
    }

    override fun HentAapenImMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> =
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(eventName),
            Log.behov(behovType),
            Log.transaksjonId(transaksjonId),
            Log.aapenId(aapenId)
        ) {
            "Skal hente åpen inntektsmelding.".also {
                logger.info(it)
                sikkerLogger.info(it)
            }

            val inntektsmelding = aapenImRepo.hentNyesteIm(aapenId)

            if (inntektsmelding != null) {
                "Hentet åpen inntektsmelding.".also {
                    logger.info(it)
                    sikkerLogger.info(it)
                }

                mapOf(
                    Key.EVENT_NAME to eventName.toJson(),
                    Key.UUID to transaksjonId.toJson(),
                    Key.AAPEN_ID to aapenId.toJson(),
                    Key.DATA to "".toJson(),
                    Key.AAPEN_INNTEKTMELDING to inntektsmelding.toJson(Inntektsmelding.serializer())
                )
            } else {
                haandterFeil("Fant ikke åpen inntektsmelding.", json)
            }
        }

    override fun HentAapenImMelding.haandterFeil(json: Map<Key, JsonElement>, error: Throwable): Map<Key, JsonElement> =
        haandterFeil("Klarte ikke hente åpen inntektsmelding.", json, error)

    private fun HentAapenImMelding.haandterFeil(
        feilmelding: String,
        json: Map<Key, JsonElement>,
        error: Throwable? = null
    ): Map<Key, JsonElement> {
        val fail = Fail(
            feilmelding = feilmelding,
            event = eventName,
            transaksjonId = transaksjonId,
            forespoerselId = null,
            utloesendeMelding = json.toJson()
        )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return mapOf(
            Key.FAIL to fail.toJson(Fail.serializer()),
            Key.EVENT_NAME to fail.event.toJson(),
            Key.UUID to fail.transaksjonId.toJson(),
            Key.AAPEN_ID to aapenId.toJson()
        )
    }
}
