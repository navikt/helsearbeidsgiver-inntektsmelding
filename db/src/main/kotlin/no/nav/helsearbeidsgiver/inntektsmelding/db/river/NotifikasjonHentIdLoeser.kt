package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.db.ForespoerselRepository
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class NotifikasjonHentIdLoeser(
    private val rapid: RapidsConnection,
    private val forespoerselRepo: ForespoerselRepository
) : Løser(rapid) {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation =
        River.PacketValidation {
            it.demandValues(
                Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.name,
                Key.BEHOV to BehovType.NOTIFIKASJON_HENT_ID.name
            )
            it.requireKeys(
                Key.FORESPOERSEL_ID,
                Key.UUID
            )
        }

    override fun onBehov(behov: Behov) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(EventName.FORESPOERSEL_BESVART),
            Log.behov(BehovType.NOTIFIKASJON_HENT_ID)
        ) {
            runCatching {
                loesBehov(behov)
            }
                .onFailure { e ->
                    "Ukjent feil. Republiserer melding.".also {
                        logger.error("$it Se sikker logg for mer info.")
                        sikkerLogger.error(it, e)

                        publishBehov(behov)
                    }
                }
        }
    }

    private fun loesBehov(behov: Behov) {
        logger.info("Mottok melding med behov '${BehovType.NOTIFIKASJON_HENT_ID}'.")

        MdcUtils.withLogFields(
            Log.forespoerselId(UUID.fromString(behov.forespoerselId))
        ) {
            hentNotifikasjonId(
                behov
            )
        }
    }

    private fun hentNotifikasjonId(behov: Behov) {
        val sakId = forespoerselRepo.hentSakId(behov.forespoerselId!!)
        "Fant sakId '$sakId'.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        val oppgaveId = forespoerselRepo.hentOppgaveId(behov.forespoerselId!!)
        "Fant oppgaveId '$oppgaveId'.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        if (sakId != null && oppgaveId != null) {
            rapid.publish(
                Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
                DataFelt.SAK_ID to sakId.toJson(),
                DataFelt.OPPGAVE_ID to oppgaveId.toJson(),
                Key.FORESPOERSEL_ID to behov.forespoerselId!!.toJson(),
                Key.UUID to behov[Key.UUID].asText().toJson()
            )
        } else if (oppgaveId != null) {
            logger.warn("Fant ikke sakId, ferdigstiller kun oppgave for ${behov.forespoerselId}!")
            rapid.publish(
                Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
                DataFelt.OPPGAVE_ID to oppgaveId.toJson(),
                Key.FORESPOERSEL_ID to behov.forespoerselId!!.toJson(),
                Key.UUID to behov[Key.UUID].asText().toJson()
            )
        } else {
            "Klarte ikke hente notifikasjons-ID-er. Begge er 'null'.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
            // publishBehov(behov)
        }
    }
}
