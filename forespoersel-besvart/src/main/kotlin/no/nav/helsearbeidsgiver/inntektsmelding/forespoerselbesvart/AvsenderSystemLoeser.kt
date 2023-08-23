package no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart

import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.utils.AvsenderSystemData
import no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart.spinn.SpinnApiException
import no.nav.helsearbeidsgiver.inntektsmelding.forespoerselbesvart.spinn.SpinnKlient
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class AvsenderSystemLoeser(
    rapidsConnection: RapidsConnection,
    private val spinnKlient: SpinnKlient
) : Løser(rapidsConnection) {

    private val logger = logger()
    val sikkerlogger = sikkerLogger()
    private val BEHOV = BehovType.HENT_AVSENDER_SYSTEM
    override fun accept():  River.PacketValidation =
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
        val inntektsmeldingId = behov[DataFelt.SPINN_INNTEKTSMELDING_ID].asText()
        try {
            val avsenderSystem = spinnKlient.hentAvsenderSystemData(inntektsmeldingId)
            publishData(behov.createData(mapOf(DataFelt.AVSENDER_SYSTEM_DATA to avsenderSystem.toJson(AvsenderSystemData.serializer()))))
        }
        catch (e: SpinnApiException){
            "Feil ved kall mot spinn api: ${e.message}".also {
                logger.error(it)
                sikkerlogger.error(it, e)
            }
        } catch (e: Exception){
            "Ukjent feil ved kall til spinn".also {
                logger.error(it)
                sikkerlogger.error(it, e)
            }
        }
    }

    override fun onBehov(packet: JsonMessage) {
    }

}
