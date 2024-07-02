package no.nav.helsearbeidsgiver.inntektsmelding.api.aktiveorgnr

import no.nav.helse.rapids_rivers.RapidsConnection
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
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

class AktiveOrgnrProducer(
    private val rapid: RapidsConnection
) {
    init {
        logger.info("Starter ${AktiveOrgnrProducer::class.simpleName}...")
    }

    fun publish(transaksjonId: UUID, arbeidsgiverFnr: Fnr, arbeidstagerFnr: Fnr) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(EventName.AKTIVE_ORGNR_REQUESTED),
            Log.transaksjonId(transaksjonId)
        ) {
            rapid.publish(
                Key.EVENT_NAME to EventName.AKTIVE_ORGNR_REQUESTED.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.DATA to "".toJson(),
                Key.FNR to arbeidstagerFnr.toJson(),
                Key.ARBEIDSGIVER_FNR to arbeidsgiverFnr.toJson()
            )
                .also { json ->
                    "Publiserte request om aktiveorgnr.".let {
                        logger.info(it)
                        sikkerLogger.info("$it\n${json.toPretty()}")
                    }
                }
        }
    }
}
