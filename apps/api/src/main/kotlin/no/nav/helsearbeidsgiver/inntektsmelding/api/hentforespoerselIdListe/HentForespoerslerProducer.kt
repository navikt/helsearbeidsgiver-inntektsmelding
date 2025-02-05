package no.nav.helsearbeidsgiver.inntektsmelding.api.hentforespoerselIdListe

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
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
        kontekstId: UUID,
        request: HentForespoerslerRequest,
    ) {
        rapid
            .publish(
                key = UUID.randomUUID(),
                Key.EVENT_NAME to EventName.FORESPOERSLER_REQUESTED.toJson(),
                Key.KONTEKST_ID to kontekstId.toJson(),
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
