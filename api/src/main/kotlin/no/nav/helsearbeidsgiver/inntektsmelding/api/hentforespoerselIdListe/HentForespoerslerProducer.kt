package no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoerselIdListe

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import java.util.UUID

class HentForespoerslerProducer(
    private val rapid: RapidsConnection,
) {
    init {
        logger.info("Starter ${HentForespoerslerProducer::class.simpleName}...")
    }

    fun publish(
        transaksjonId: UUID,
        request: HentForespoerslerRequest,
    ) {
        rapid
            .publish(
                Key.EVENT_NAME to EventName.FORESPOERSLER_REQUESTED.toJson(EventName.serializer()),
                Key.UUID to transaksjonId.toString().toJson(),
                Key.DATA to
                    mapOf(
                        Key.VEDTAKSPERIODE_ID_LISTE to request.vedtaksperiodeIdListe.toJson(UuidSerializer),
                    ).toJson(),
            ).also { json ->
                "Publiserte til kafka.".also {
                    logger.info(it)
                    sikkerLogger.info(it + " json=${json.toPretty()}")
                }
            }
    }
}
