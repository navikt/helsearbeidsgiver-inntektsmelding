package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Event
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
    private val repository: InntektsmeldingRepository
) : Loeser(rapidsConnection) {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandValue(Key.BEHOV.str, BehovType.LAGRE_EKSTERN_INNTEKTSMELDING.name)
            it.requireKey(Key.UUID.str)
            it.interestedIn(DataFelt.EKSTERN_INNTEKTSMELDING)
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
            logger.info("Mottok behov ${BehovType.LAGRE_EKSTERN_INNTEKTSMELDING.name}")
            sikkerLogger.info("Mottok behov:\n${behov.toJsonMessage().toPretty()}")
            val eksternInntektsmelding = behov[DataFelt.EKSTERN_INNTEKTSMELDING].toString().fromJson(EksternInntektsmelding.serializer())
            if (eksternInntektsmelding == null) {
                logger.error("Fant ingen EksternInntektsmelding")
                sikkerLogger.error("Fant ingen EksternInntektsmelding")
                publishFail(behov.createFail("Klarte ikke lagre EksternInntektsmelding for transaksjonsId $transaksjonsId. Mangler datafelt"))
            } else {
                try {
                    repository.lagreEksternInntektsmelding(forespoerselId, eksternInntektsmelding)
                    logger.info(
                        "Lagret EksternInntektsmelding med arkiv referanse ${eksternInntektsmelding.arkivreferanse}" +
                            " i database for forespoerselId $forespoerselId"
                    )
                    publishEvent(Event.create(EventName.EKSTERN_INNTEKTSMELDING_LAGRET, forespoerselId, mapOf(Key.UUID to transaksjonsId)))
                } catch (ex: Exception) {
                    publishFail(behov.createFail("Klarte ikke lagre EksternInntektsmelding for transaksjonsId $transaksjonsId"))
                    logger.error("Klarte ikke lagre EksternInntektsmelding")
                    sikkerLogger.error(
                        "Klarte ikke lagre EksternInntektsmelding $EksternInntektsmelding",
                        ex
                    )
                }
            }
        }
    }
}
