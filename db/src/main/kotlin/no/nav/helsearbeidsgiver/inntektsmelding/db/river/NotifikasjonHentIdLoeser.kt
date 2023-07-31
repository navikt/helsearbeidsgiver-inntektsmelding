package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Løser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandValues
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireKeys
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.db.ForespoerselRepository
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
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
                Key.TRANSACTION_ORIGIN
            )
        }

    override fun onBehov(packet: JsonMessage) {
        val json = packet.toJson().parseJson()

        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.event(EventName.FORESPOERSEL_BESVART),
            Log.behov(BehovType.NOTIFIKASJON_HENT_ID)
        ) {
            runCatching {
                json.loesBehov()
            }
                .onFailure { e ->
                    "Ukjent feil. Republiserer melding.".also {
                        logger.error("$it Se sikker logg for mer info.")
                        sikkerLogger.error(it, e)

                        json.republiser()
                    }
                }
        }
    }

    private fun JsonElement.loesBehov() {
        logger.info("Mottok melding med behov '${BehovType.NOTIFIKASJON_HENT_ID}'.")
        sikkerLogger.info("Mottok melding:\n${toPretty()}")

        val melding = fromJsonMapFiltered(Key.serializer())

        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)
        val transaksjonId = Key.TRANSACTION_ORIGIN.les(UuidSerializer, melding)

        MdcUtils.withLogFields(
            Log.forespoerselId(forespoerselId),
            Log.transaksjonId(transaksjonId)
        ) {
            hentNotifikasjonId(
                forespoerselId = forespoerselId,
                transaksjonId = transaksjonId
            )
        }
    }

    private fun JsonElement.hentNotifikasjonId(forespoerselId: UUID, transaksjonId: UUID) {
        val sakId = forespoerselRepo.hentSakId(forespoerselId.toString())
        "Fant sakId '$sakId'.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        val oppgaveId = forespoerselRepo.hentOppgaveId(forespoerselId.toString())
        "Fant oppgaveId '$oppgaveId'.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        if (sakId != null && oppgaveId != null) {
            rapid.publish(
                Key.EVENT_NAME to EventName.FORESPOERSEL_BESVART.toJson(),
                DataFelt.SAK_ID to sakId.toJson(),
                DataFelt.OPPGAVE_ID to oppgaveId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.TRANSACTION_ORIGIN to transaksjonId.toJson()
            )
        } else {
            "Klarte ikke hente notifikasjons-ID-er. Én eller flere er 'null'. Republiserer melding.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
            republiser()
        }
    }

    private fun JsonElement.republiser() {
        rapid.publish(toString())
    }
}
