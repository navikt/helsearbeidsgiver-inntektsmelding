package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Innsending
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.inntektsmelding.db.mapInntektsmeldingDokument
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class PersisterImLoeser(rapidsConnection: RapidsConnection, private val repository: InntektsmeldingRepository) : Loeser(rapidsConnection) {

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
                DataFelt.ARBEIDSGIVER_INFORMASJON,
                Key.FORESPOERSEL_ID
            )
        }

    override fun onBehov(behov: Behov) {
        logger.info("Løser behov $PERSISTER_IM med id ${behov.forespoerselId}")
        try {
            val arbeidsgiver = behov[DataFelt.VIRKSOMHET].asText()
            sikkerLogger.info("Fant arbeidsgiver: $arbeidsgiver")
            val arbeidstakerInfo = behov[DataFelt.ARBEIDSTAKER_INFORMASJON].toJsonElement().fromJson(PersonDato.serializer())
            val arbeidsgiverInfo = behov[DataFelt.ARBEIDSGIVER_INFORMASJON].toJsonElement().fromJson(PersonDato.serializer())
            val fulltNavn = arbeidstakerInfo.navn
            sikkerLogger.info("Fant fulltNavn: $fulltNavn")
            val innsending = behov[DataFelt.INNTEKTSMELDING].toString().fromJson(Innsending.serializer())
            val inntektsmeldingDokument = mapInntektsmeldingDokument(innsending, fulltNavn, arbeidsgiver, arbeidsgiverInfo.navn)
            repository.lagreInntektsmelding(behov.forespoerselId!!, inntektsmeldingDokument)
            sikkerLogger.info("Lagret Inntektsmelding for forespoerselId: ${behov.forespoerselId}")
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
}
