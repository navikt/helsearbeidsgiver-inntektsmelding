package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.mapInntektsmeldingDokument
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class PersisterImLøser(rapidsConnection: RapidsConnection, private val repository: InntektsmeldingRepository) : Løser(rapidsConnection) {

    private val PERSISTER_IM = BehovType.PERSISTER_IM
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation =
        River.PacketValidation {
            it.demandValues(
                Key.BEHOV to PERSISTER_IM.name
            )
            it.interestedIn(
                DataFelt.INNTEKTSMELDING,
                DataFelt.VIRKSOMHET,
                DataFelt.ARBEIDSTAKER_INFORMASJON,
                Key.FORESPOERSEL_ID
            )
        }

    override fun onBehov(behov: Behov) {
        logger.info("Løser behov $PERSISTER_IM med id ${behov.forespoerselId}")
        try {
            val arbeidsgiver = behov[DataFelt.VIRKSOMHET].asText()
            sikkerLogger.info("Fant arbeidsgiver: $arbeidsgiver")
            val arbeidstakerInfo = customObjectMapper().treeToValue(behov[DataFelt.ARBEIDSTAKER_INFORMASJON], PersonDato::class.java)
            val fulltNavn = arbeidstakerInfo.navn
            sikkerLogger.info("Fant fulltNavn: $fulltNavn")
            val innsendingRequest: InnsendingRequest = customObjectMapper().treeToValue(behov[DataFelt.INNTEKTSMELDING], InnsendingRequest::class.java)
            val inntektsmeldingDokument = mapInntektsmeldingDokument(innsendingRequest, fulltNavn, arbeidsgiver)
            repository.lagreInntektsmelding(behov.forespoerselId!!, inntektsmeldingDokument)
            sikkerLogger.info("Lagret InntektsmeldingDokument for forespoerselId: ${behov.forespoerselId}")
            behov.createData(
                mapOf(
                    DataFelt.INNTEKTSMELDING_DOKUMENT to inntektsmeldingDokument
                )
            ).also {
                publishData(it)
            }
        } catch (ex: Exception) {
            logger.error("Klarte ikke persistere: ${behov.forespoerselId}")
            sikkerLogger.error("Klarte ikke persistere: ${behov.forespoerselId}", ex)
            behov.createFail("Klarte ikke persistere: ${behov.forespoerselId}").also { publishFail(it) }
        }
    }

    override fun onBehov(packet: JsonMessage) {
    }
}
