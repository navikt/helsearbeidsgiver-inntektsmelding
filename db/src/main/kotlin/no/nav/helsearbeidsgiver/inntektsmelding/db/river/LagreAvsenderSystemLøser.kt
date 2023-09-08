package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.AvsenderSystemData
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Event
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.*

class LagreAvsenderSystemLøser(
    rapidsConnection: RapidsConnection,
    private val repository: InntektsmeldingRepository
) : Løser(rapidsConnection) {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandValue(Key.BEHOV.str, BehovType.LAGRE_AVSENDER_SYSTEM.name)
            it.requireKey(Key.UUID.str)
            it.interestedIn(DataFelt.AVSENDER_SYSTEM_DATA)
        }
    }
    override fun onBehov(behov: Behov) {
        val transaksjonsId = behov[Key.UUID].asText()
        val forespoerselId = behov[Key.FORESPOERSEL_ID].asText()
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(behov.event),
            Log.transaksjonId(UUID.fromString(transaksjonsId)),
            Log.behov(behov.behov)
        ) {
            logger.info("Mottok behov ${BehovType.LAGRE_AVSENDER_SYSTEM.name}")
            sikkerLogger.info("Mottok behov:\n${behov.toJsonMessage()}")
            val avsenderSystemData = behov[DataFelt.AVSENDER_SYSTEM_DATA].toString().fromJson(AvsenderSystemData.serializer())
            if (avsenderSystemData == null) {
                logger.error("Fant ingen AvsenderSystemData")
                sikkerLogger.error("Fant ingen AvsenderSystemData")
                publishFail(behov.createFail("Klarte ikke lagre AvsenderSystemData for transaksjonsId $transaksjonsId. Mangler datafelt"))
            } else {
                try {
                    repository.lagreAvsenderSystemData(forespoerselId, avsenderSystemData)
                    logger.info(
                        "Lagret AvsenderSystemData med arkiv referanse ${avsenderSystemData.arkivreferanse}" +
                            " i database for forespoerselId $forespoerselId"
                    )
                    publishEvent(Event.create(EventName.AVSENDER_SYSTEM_LAGRET, forespoerselId, mapOf(Key.UUID to transaksjonsId)))
                } catch (ex: Exception) {
                    publishFail(behov.createFail("Klarte ikke lagre AvsenderSystemData for transaksjonsId $transaksjonsId"))
                    logger.error("Klarte ikke lagre AvsenderSystemData")
                    sikkerLogger.error(
                        "Klarte ikke lagre AvsenderSystemData $AvsenderSystemData",
                        ex
                    )
                }
            }
        }
    }
}
