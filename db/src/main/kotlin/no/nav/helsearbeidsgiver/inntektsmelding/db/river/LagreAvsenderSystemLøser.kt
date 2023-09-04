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
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

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
        logger.info("LagreAvsenderSystemLøser behov ${BehovType.LAGRE_AVSENDER_SYSTEM.name} med transaksjonsId $transaksjonsId")
        sikkerLogger.info("LagreAvsenderSystemLøser fikk pakke:\n${behov.toJsonMessage()}")
        val avsenderSystemData = behov[DataFelt.AVSENDER_SYSTEM_DATA].toString().fromJson(AvsenderSystemData.serializer())
        if (avsenderSystemData == null) {
            logger.error("LagreAvsenderSystemLøser fant ingen AvsenderSystemData for transaksjonsId $transaksjonsId")
            sikkerLogger.error("LagreAvsenderSystemLøser fant ingen AvsenderSystemData for transaksjonsId $transaksjonsId")
            publishFail(behov.createFail("Klarte ikke lagre AvsenderSystemData for transaksjonsId $transaksjonsId. Mangler datafelt"))
        } else {
            try {
                repository.lagreAvsenderSystemData(forespoerselId, avsenderSystemData)
                logger.info(
                    "LagreAvsenderSystemLøser lagret AvsenderSystemData med arkiv referanse ${avsenderSystemData.arkivreferanse} i database for forespoerselId $forespoerselId"
                )
                publishEvent(Event.create(EventName.AVSENDER_SYSTEM_LAGRET, forespoerselId))
            } catch (ex: Exception) {
                publishFail(behov.createFail("Klarte ikke lagre AvsenderSystemData for transaksjonsId $transaksjonsId"))
                logger.error("LagreAvsenderSystemLøser klarte ikke lagre AvsenderSystemData for transaksjonsId $transaksjonsId")
                sikkerLogger.error("LagreAvsenderSystemLøser klarte ikke lagre AvsenderSystemData $AvsenderSystemData for transaksjonsId $transaksjonsId", ex)
            }
        }
    }
}
