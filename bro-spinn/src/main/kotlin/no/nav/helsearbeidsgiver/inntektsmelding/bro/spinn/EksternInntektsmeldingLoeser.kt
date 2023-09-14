package no.nav.helsearbeidsgiver.inntektsmelding.bro.spinn

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.inntektsmelding.bro.spinn.spinn.SpinnApiException
import no.nav.helsearbeidsgiver.inntektsmelding.bro.spinn.spinn.SpinnKlient
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class EksternInntektsmeldingLoeser(
    rapidsConnection: RapidsConnection,
    private val spinnKlient: SpinnKlient
) : Løser(rapidsConnection) {

    private val logger = logger()
    val sikkerlogger = sikkerLogger()
    private val BEHOV = BehovType.HENT_EKSTERN_INNTEKTSMELDING
    override fun accept(): River.PacketValidation =
        River.PacketValidation {
            it.demandValues(
                Key.BEHOV to BEHOV.name
            )
            it.interestedIn(
                DataFelt.SPINN_INNTEKTSMELDING_ID,
                Key.UUID
            )
        }

    override fun onBehov(behov: Behov) {
        logger.info("Løser behov $BEHOV med uuid ${behov.uuid()}")
        val inntektsmeldingId = behov[DataFelt.SPINN_INNTEKTSMELDING_ID]
        if (inntektsmeldingId.isMissingOrNull() || inntektsmeldingId.asText().isEmpty()) {
            publishFail(behov.createFail("Mangler inntektsmeldingId"))
            return
        }
        try {
            val avsenderSystem = spinnKlient.hentEksternInntektsmelding(inntektsmeldingId.asText())
            publishData(behov.createData(mapOf(DataFelt.EKSTERN_INNTEKTSMELDING to avsenderSystem)))
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
