package no.nav.helsearbeidsgiver.inntektsmelding.api.lagreselvbestemtim

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import java.util.UUID

// TODO test
class LagreSelvbestemtImProducer(
    private val rapid: RapidsConnection
) {
    init {
        logger.info("Starter ${LagreSelvbestemtImProducer::class.simpleName}...")
    }

    fun publish(selvbestemtId: UUID, avsenderFnr: String, skjema: SkjemaInntektsmelding): UUID {
        val clientId = UUID.randomUUID()

        MdcUtils.withLogFields(
            Log.event(EventName.SELVBESTEMT_IM_MOTTATT),
            Log.clientId(clientId),
            Log.selvbestemtId(selvbestemtId)
        ) {
            rapid.publish(
                Key.EVENT_NAME to EventName.SELVBESTEMT_IM_MOTTATT.toJson(),
                Key.CLIENT_ID to clientId.toJson(),
                Key.SELVBESTEMT_ID to selvbestemtId.toJson(),
                Key.SKJEMA_INNTEKTSMELDING to skjema.toJson(SkjemaInntektsmelding.serializer()),
                Key.ARBEIDSGIVER_FNR to avsenderFnr.toJson()
            )
                .also {
                    logger.info("Publiserte til kafka.")
                    sikkerLogger.info("Publiserte til kafka:\n${it.toPretty()}")
                }
        }

        return clientId
    }
}
