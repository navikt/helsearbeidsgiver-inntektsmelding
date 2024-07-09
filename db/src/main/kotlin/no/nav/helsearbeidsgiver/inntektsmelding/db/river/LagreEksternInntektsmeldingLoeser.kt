package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.publishEvent
import no.nav.helsearbeidsgiver.felles.rapidsrivers.toPretty
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class LagreEksternInntektsmeldingLoeser(
    rapidsConnection: RapidsConnection,
    private val repository: InntektsmeldingRepository,
) : Loeser(rapidsConnection) {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandValue(Key.BEHOV.str, BehovType.LAGRE_EKSTERN_INNTEKTSMELDING.name)
            it.requireKey(Key.UUID.str)
            it.interestedIn(Key.EKSTERN_INNTEKTSMELDING)
        }
    }

    override fun onBehov(behov: Behov) {
        val transaksjonId = behov[Key.UUID].asText().let(UUID::fromString)
        val forespoerselId = behov[Key.FORESPOERSEL_ID].asText().let(UUID::fromString)
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(behov.event),
            Log.transaksjonId(transaksjonId),
            Log.behov(behov.behov),
        ) {
            logger.info("Mottok behov ${BehovType.LAGRE_EKSTERN_INNTEKTSMELDING.name}")
            sikkerLogger.info("Mottok behov:\n${behov.jsonMessage.toPretty()}")
            val eksternInntektsmelding = behov[Key.EKSTERN_INNTEKTSMELDING].toString().fromJson(EksternInntektsmelding.serializer())
            if (eksternInntektsmelding == null) {
                logger.error("Fant ingen EksternInntektsmelding")
                sikkerLogger.error("Fant ingen EksternInntektsmelding")
                publishFail(behov.createFail("Klarte ikke lagre EksternInntektsmelding for transaksjonId $transaksjonId. Mangler datafelt"))
            } else {
                try {
                    repository.lagreEksternInntektsmelding(forespoerselId.toString(), eksternInntektsmelding)
                    logger.info(
                        "Lagret EksternInntektsmelding med arkiv referanse ${eksternInntektsmelding.arkivreferanse}" +
                            " i database for forespoerselId $forespoerselId",
                    )

                    rapidsConnection.publishEvent(
                        eventName = EventName.EKSTERN_INNTEKTSMELDING_LAGRET,
                        transaksjonId = transaksjonId,
                        forespoerselId = forespoerselId,
                    )
                } catch (ex: Exception) {
                    publishFail(behov.createFail("Klarte ikke lagre EksternInntektsmelding for transaksjonId $transaksjonId"))
                    logger.error("Klarte ikke lagre EksternInntektsmelding")
                    sikkerLogger.error(
                        "Klarte ikke lagre EksternInntektsmelding $EksternInntektsmelding",
                        ex,
                    )
                }
            }
        }
    }
}
