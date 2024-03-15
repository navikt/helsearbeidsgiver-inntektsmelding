package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.db.ForespoerselRepository
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class NotifikasjonHentIdLoeser(
    private val rapid: RapidsConnection,
    private val forespoerselRepo: ForespoerselRepository
) : Loeser(rapid) {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation =
        River.PacketValidation {
            it.demandValues(
                Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.name,
                Key.BEHOV to BehovType.NOTIFIKASJON_HENT_ID.name
            )
            it.requireKeys(
                Key.UUID,
                Key.FORESPOERSEL_ID
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
                    "Ukjent feil.".also {
                        logger.error("$it Se sikker logg for mer info.")
                        sikkerLogger.error(it, e)
                    }
                }
        }
    }

    private fun loesBehov(behov: Behov) {
        logger.info("Mottok melding med behov '${BehovType.NOTIFIKASJON_HENT_ID}'.")

        val json = behov.jsonMessage.toJson().parseJson()

        val transaksjonId = json.toMap()[Key.UUID]?.fromJson(UuidSerializer)
        val forespoerselId = behov.forespoerselId?.let(UUID::fromString)

        if (transaksjonId != null && forespoerselId != null) {
            MdcUtils.withLogFields(
                Log.transaksjonId(transaksjonId),
                Log.forespoerselId(forespoerselId)
            ) {
                hentNotifikasjonId(transaksjonId, forespoerselId)
            }
        } else {
            "Mangler transaksjonId og/eller forespoerselId.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
        }
    }

    private fun hentNotifikasjonId(transaksjonId: UUID, forespoerselId: UUID) {
        val sakId = forespoerselRepo.hentSakId(forespoerselId)
        "Fant sakId '$sakId'.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        val oppgaveId = forespoerselRepo.hentOppgaveId(forespoerselId)
        "Fant oppgaveId '$oppgaveId'.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        if (sakId != null) {
            rapid.publish(
                Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.SAK_ID to sakId.toJson()
            )
        } else {
            "Fant ikke sakId.".also {
                logger.error(it) // TODO: bare logg en notis?
                sikkerLogger.error(it)
            }
        }

        if (oppgaveId != null) {
            rapid.publish(
                Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.OPPGAVE_ID to oppgaveId.toJson()
            )
        } else {
            "Fant ikke oppgaveId.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
        }
    }
}
