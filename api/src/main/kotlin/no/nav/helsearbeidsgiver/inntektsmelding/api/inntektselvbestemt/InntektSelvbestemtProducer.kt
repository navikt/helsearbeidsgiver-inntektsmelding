package no.nav.helsearbeidsgiver.inntektsmelding.api.inntektselvbestemt

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
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class InntektSelvbestemtProducer(
    private val rapid: RapidsConnection
) {
    init {
        logger.info("Starter ${InntektSelvbestemtProducer::class.simpleName}...")
    }

    fun publish(request: InntektSelvbestemtRequest): UUID {
        val transaksjonId = UUID.randomUUID()

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(EventName.INNTEKT_SELVBESTEMT_REQUESTED),
            Log.transaksjonId(transaksjonId)
        ) {
            rapid.publish(
                Key.EVENT_NAME to EventName.INNTEKT_SELVBESTEMT_REQUESTED.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FNR to request.sykmeldtFnr.toJson(Fnr.serializer()),
                Key.ORGNRUNDERENHET to request.orgnr.toJson(Orgnr.serializer()),
                Key.SKJAERINGSTIDSPUNKT to request.inntektsdato.toJson()
            )
                .also { json ->
                    "Publiserte request om inntekt selvbestemt.".let {
                        logger.info(it)
                        sikkerLogger.info("$it\n${json.toPretty()}")
                    }
                }
        }

        return transaksjonId
    }
}
