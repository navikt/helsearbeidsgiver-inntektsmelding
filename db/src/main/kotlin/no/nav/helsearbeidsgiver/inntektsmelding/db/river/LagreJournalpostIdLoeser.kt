package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.publishEvent
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class LagreJournalpostIdLoeser(
    rapidsConnection: RapidsConnection,
    private val repository: InntektsmeldingRepository
) : Loeser(rapidsConnection) {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandValue(Key.BEHOV.str, BehovType.LAGRE_JOURNALPOST_ID.name)
            it.requireKey(Key.UUID.str)
            it.requireKey(Key.JOURNALPOST_ID.str)
        }
    }

    override fun onBehov(behov: Behov) {
        MdcUtils.withLogFields(
            Log.klasse(this),
            Log.behov(BehovType.LAGRE_JOURNALPOST_ID)
        ) {
            val json = behov.jsonMessage.toJson().parseJson()

            logger.info("Mottok melding.")
            sikkerLogger.info("Mottok melding:\n${json.toPretty()}")

            val melding = json.toMap()

            val event = Key.EVENT_NAME.les(EventName.serializer(), melding)
            val transaksjonId = Key.UUID.les(UuidSerializer, melding)

            val forespoerselId = behov.forespoerselId!!.let(UUID::fromString)

            MdcUtils.withLogFields(
                Log.event(event),
                Log.transaksjonId(transaksjonId),
                Log.forespoerselId(forespoerselId)
            ) {
                val journalpostId = Key.JOURNALPOST_ID.lesOrNull(String.serializer(), melding)
                lagreJournalpostId(behov, journalpostId, forespoerselId, transaksjonId)
            }
        }
    }

    private fun lagreJournalpostId(behov: Behov, journalpostId: String?, forespoerselId: UUID, transaksjonId: UUID) {
        if (journalpostId.isNullOrBlank()) {
            logger.error("Fant ingen journalpost-ID.")
            sikkerLogger.error("Fant ingen journalpost-ID.")
            behov.createFail("Klarte ikke lagre journalpostId for transaksjonId $transaksjonId. Tom journalpost-ID!")
                .also { publishFail(it) }
        } else {
            try {
                repository.oppdaterJournalpostId(forespoerselId, journalpostId)

                logger.info("Lagret journalpost-ID $journalpostId i database.")
                sikkerLogger.info("Lagret journalpost-ID $journalpostId i database.")

                val inntektsmelding = repository.hentNyeste(forespoerselId)

                rapidsConnection.publishEvent(
                    eventName = EventName.INNTEKTSMELDING_JOURNALFOERT,
                    transaksjonId = null,
                    forespoerselId = forespoerselId,
                    Key.JOURNALPOST_ID to journalpostId.toJson(),
                    Key.INNTEKTSMELDING_DOKUMENT to inntektsmelding?.toJson(Inntektsmelding.serializer())
                )
            } catch (ex: Exception) {
                behov.createFail("Klarte ikke lagre journalpostId for transaksjonId $transaksjonId")
                    .also { publishFail(it) }
                logger.error("Klarte ikke lagre journalpost-ID $journalpostId.")
                sikkerLogger.error("Klarte ikke lagre journalpost-ID $journalpostId.", ex)
            }
        }
    }
}
