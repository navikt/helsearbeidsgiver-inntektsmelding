package no.nav.helsearbeidsgiver.inntektsmelding.brospinn

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.publishData
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

// TODO slett etter overgangsperiode
class EksternInntektsmeldingLoeser(
    rapidsConnection: RapidsConnection,
    private val spinnKlient: SpinnKlient,
) : Loeser(rapidsConnection) {
    private val logger = logger()
    private val sikkerlogger = sikkerLogger()
    private val behov = BehovType.HENT_EKSTERN_INNTEKTSMELDING

    override fun accept(): River.PacketValidation =
        River.PacketValidation {
            it.demandValues(
                Key.BEHOV to behov.name,
            )
            it.requireKeys(
                Key.SPINN_INNTEKTSMELDING_ID,
                Key.UUID,
            )
        }

    override fun onBehov(behov: Behov) {
        try {
            val json = behov.jsonMessage.toJson().parseJson().toMap()

            val transaksjonId = Key.UUID.les(UuidSerializer, json)

            logger.info("Løser behov ${this.behov} med transaksjonId $transaksjonId")

            val inntektsmeldingId = Key.SPINN_INNTEKTSMELDING_ID.lesOrNull(UuidSerializer, json)
            if (inntektsmeldingId == null) {
                publishFail(behov.createFail("Mangler inntektsmeldingId"))
                return
            }

            val eksternInntektsmelding = spinnKlient.hentEksternInntektsmelding(inntektsmeldingId)

            rapidsConnection.publishData(
                eventName = behov.event,
                transaksjonId = transaksjonId,
                forespoerselId = behov.forespoerselId?.let(UUID::fromString),
                Key.EKSTERN_INNTEKTSMELDING to eksternInntektsmelding.toJson(EksternInntektsmelding.serializer()),
            )
        } catch (e: SpinnApiException) {
            "Feil ved kall mot spinn api: ${e.message}".also {
                logger.error(it)
                sikkerlogger.error(it, e)
                publishFail(behov.createFail(it))
            }
        } catch (e: Exception) {
            "Ukjent feil ved kall til spinn".also {
                logger.error(it)
                sikkerlogger.error(it, e)
                publishFail(behov.createFail(it))
            }
        }
    }
}
