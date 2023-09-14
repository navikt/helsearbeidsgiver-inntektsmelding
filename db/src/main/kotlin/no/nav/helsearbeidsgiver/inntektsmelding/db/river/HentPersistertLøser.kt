package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.Jackson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.interestedIn
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import kotlin.system.measureTimeMillis

private const val EMPTY_PAYLOAD = "{}"

class HentPersistertLøser(rapidsConnection: RapidsConnection, private val repository: InntektsmeldingRepository) : Loeser(rapidsConnection) {

    private val BEHOV = BehovType.HENT_PERSISTERT_IM
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation =
        River.PacketValidation {
            it.demandValues(
                Key.BEHOV to BEHOV.name
            )
            it.interestedIn(
                Key.EVENT_NAME,
                Key.FORESPOERSEL_ID
            )
        }

    override fun onBehov(behov: Behov) {
        measureTimeMillis {
            logger.info("Skal hente persistert inntektsmelding med forespørselId ${behov.forespoerselId}")
            try {
                val dokument = repository.hentNyeste(behov.forespoerselId!!)
                if (dokument == null) {
                    logger.info("Fant IKKE persistert inntektsmelding for forespørselId ${behov.forespoerselId}")
                } else {
                    sikkerLogger.info("Fant persistert inntektsmelding: $dokument for forespørselId ${behov.forespoerselId}")
                }
                behov.createData(
                    mapOf(
                        DataFelt.INNTEKTSMELDING_DOKUMENT to if (dokument == null) {
                            EMPTY_PAYLOAD
                        } else {
                            Jackson.toJson(dokument)
                        }
                    )
                )
                    .also {
                        publishData(it)
                    }
            } catch (ex: Exception) {
                logger.info("Det oppstod en feil ved uthenting av persistert inntektsmelding for forespørselId ${behov.forespoerselId}")
                sikkerLogger.error(
                    "Det oppstod en feil ved uthenting av persistert inntektsmelding for forespørselId ${behov.forespoerselId}",
                    ex
                )
                behov.createFail("Klarte ikke hente persistert inntektsmelding").also {
                    publishFail(it)
                }
            }
        }.also {
            logger.info("Hent inntektmelding fra DB took $it")
        }
    }
}
