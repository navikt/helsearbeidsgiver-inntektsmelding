package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Innsending
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.publishData
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.erDuplikatAv
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class PersisterImSkjemaLoeser(
    rapidsConnection: RapidsConnection,
    private val repository: InntektsmeldingRepository,
) : Loeser(rapidsConnection) {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation =
        River.PacketValidation {
            it.demandValues(
                Key.BEHOV to BehovType.PERSISTER_IM_SKJEMA.name,
            )
            it.interestedIn(
                Key.FORESPOERSEL_ID,
                Key.SKJEMA_INNTEKTSMELDING,
            )
        }

    override fun onBehov(behov: Behov) {
        val forespoerselId = behov.forespoerselId!!.let(UUID::fromString)

        logger.info("Løser behov ${BehovType.PERSISTER_IM_SKJEMA} med id $forespoerselId")

        try {
            val json =
                behov.jsonMessage
                    .toJson()
                    .parseJson()
                    .toMap()

            val transaksjonId = Key.UUID.lesOrNull(UuidSerializer, json)
            val inntektsmeldingSkjema = Key.SKJEMA_INNTEKTSMELDING.les(Innsending.serializer(), json)

            val sisteIm =
                repository.hentNyesteInntektsmeldingSkjema(forespoerselId) // TODO: Trenger vi egentlig noen duplikatsjekk her, eller skal vi bare lagre ned alt?
            val sisteImSkjema = repository.hentNyesteInntektsmeldingSkjema(forespoerselId)
            val erDuplikat =
                sisteIm?.erDuplikatAv(inntektsmeldingSkjema) ?: false ||
                    sisteImSkjema?.erDuplikatAv(inntektsmeldingSkjema) ?: false

            if (erDuplikat) {
                sikkerLogger.warn("Fant duplikat av inntektsmelding for forespoerselId: $forespoerselId")
            } else {
                repository.lagreInntektsmeldingSkjema(forespoerselId.toString(), inntektsmeldingSkjema)
                sikkerLogger.info("Lagret Inntektsmelding for forespoerselId: $forespoerselId")
            }

            val bumerangdata =
                json
                    .minus(listOf(Key.BEHOV, Key.EVENT_NAME))
                    .toList()
                    .toTypedArray()

            rapidsConnection.publishData(
                eventName = behov.event,
                transaksjonId = transaksjonId,
                forespoerselId = forespoerselId,
                Key.PERSISTERT_SKJEMA_INNTEKTSMELDING to inntektsmeldingSkjema.toJson(Innsending.serializer()),
                Key.ER_DUPLIKAT_IM to erDuplikat.toJson(Boolean.serializer()),
                *bumerangdata
            )
        } catch (ex: Exception) {
            "Klarte ikke persistere skjema: $forespoerselId".also {
                logger.error(it)
                sikkerLogger.error(it, ex)
                behov.createFail(it).also { publishFail(it) }
            }
        }
    }
}
