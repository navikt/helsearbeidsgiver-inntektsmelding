package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import kotlinx.serialization.builtins.serializer
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
import no.nav.helsearbeidsgiver.inntektsmelding.db.SelvbestemtImRepo
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.pipe.orDefault
import java.util.UUID

data class LagreSelvbestemtImMelding(
    val eventName: EventName,
    val behovType: BehovType,
    val transaksjonId: UUID,
    val selvbestemtInntektsmelding: Inntektsmelding
)

class LagreSelvbestemtImRiver(
    private val selvbestemtImRepo: SelvbestemtImRepo
) : ObjectRiver<LagreSelvbestemtImMelding>() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): LagreSelvbestemtImMelding? =
        if (setOf(Key.DATA, Key.FAIL).any(json::containsKey)) {
            null
        } else {
            LagreSelvbestemtImMelding(
                eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
                behovType = Key.BEHOV.krev(BehovType.LAGRE_SELVBESTEMT_IM, BehovType.serializer(), json),
                transaksjonId = Key.UUID.les(UuidSerializer, json),
                selvbestemtInntektsmelding = Key.SELVBESTEMT_INNTEKTSMELDING.les(Inntektsmelding.serializer(), json)
            )
        }

    override fun LagreSelvbestemtImMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        "Skal lagre selvbestemt inntektsmelding.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        val nyesteIm = selvbestemtImRepo.hentNyesteIm(selvbestemtInntektsmelding.id)

        val erDuplikat = nyesteIm?.erDuplikatAv(selvbestemtInntektsmelding).orDefault(false)

        if (!erDuplikat) {
            selvbestemtImRepo.lagreIm(selvbestemtInntektsmelding)

            "Lagret selvbestemt inntektsmelding.".also {
                logger.info(it)
                sikkerLogger.info(it)
            }
        } else {
            "Lagret _ikke_ selvbestemt inntektsmelding pga. duplikat.".also {
                logger.info(it)
                sikkerLogger.info(it)
            }
        }

        return mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to "".toJson(),
            Key.SELVBESTEMT_INNTEKTSMELDING to selvbestemtInntektsmelding.toJson(Inntektsmelding.serializer()),
            Key.ER_DUPLIKAT_IM to erDuplikat.toJson(Boolean.serializer())
        )
    }

    override fun LagreSelvbestemtImMelding.haandterFeil(json: Map<Key, JsonElement>, error: Throwable): Map<Key, JsonElement> {
        val fail = Fail(
            feilmelding = "Klarte ikke lagre selvbestemt inntektsmelding.",
            event = eventName,
            transaksjonId = transaksjonId,
            forespoerselId = null,
            utloesendeMelding = json.toJson()
        )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
            .minus(Key.FORESPOERSEL_ID)
            .plus(Key.SELVBESTEMT_ID to selvbestemtInntektsmelding.id.toJson())
    }

    override fun LagreSelvbestemtImMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@LagreSelvbestemtImRiver),
            Log.event(eventName),
            Log.behov(behovType),
            Log.transaksjonId(transaksjonId),
            Log.selvbestemtId(selvbestemtInntektsmelding.id)
        )
}

private fun Inntektsmelding.erDuplikatAv(other: Inntektsmelding): Boolean =
    this == other.copy(
        avsender = other.avsender.copy(
            fnr = avsender.fnr,
            navn = avsender.navn,
            tlf = avsender.tlf
        ),
        aarsakInnsending = aarsakInnsending,
        mottatt = mottatt
    )
