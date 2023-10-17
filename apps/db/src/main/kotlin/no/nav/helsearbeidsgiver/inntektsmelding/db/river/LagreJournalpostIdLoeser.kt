package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJsonNode
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.utils.mapOfNotNull
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

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
        logger.info("LagreJournalpostIdLøser behov ${BehovType.LAGRE_JOURNALPOST_ID.name} med transaksjonsId ${behov.uuid()}")
        val journalpostId = behov[Key.JOURNALPOST_ID].asText()
        if (journalpostId.isNullOrBlank()) {
            logger.error("LagreJournalpostIdLøser fant ingen journalpostId for transaksjonsId ${behov.uuid()}")
            sikkerLogger.error("LagreJournalpostIdLøser fant ingen journalpostId for transaksjonsId ${behov.uuid()}")
            behov.createFail("Klarte ikke lagre journalpostId for transaksjonsId ${behov.uuid()}. Tom journalpostID!!")
                .also { publishFail(it) }
        } else {
            try {
                repository.oppdaterJournalpostId(journalpostId, behov.forespoerselId!!)
                logger.info("LagreJournalpostIdLøser lagret journalpostId $journalpostId i database for forespoerselId ${behov.forespoerselId}")
                val inntektsmelding = repository.hentNyeste(behov.forespoerselId!!)
                behov.createEvent(
                    EventName.INNTEKTSMELDING_JOURNALFOERT,
                    mapOfNotNull(
                        Key.JOURNALPOST_ID to journalpostId,
                        DataFelt.INNTEKTSMELDING_DOKUMENT to inntektsmelding?.toJson(Inntektsmelding.serializer())?.toJsonNode()
                    )
                )
                    .also { publishEvent(it) }
            } catch (ex: Exception) {
                behov.createFail("Klarte ikke lagre journalpostId for transaksjonsId ${behov.uuid()}")
                    .also { publishFail(it) }
                logger.error("LagreJournalpostIdLøser klarte ikke lagre journalpostId $journalpostId for transaksjonsId ${behov.uuid()}")
                sikkerLogger.error("LagreJournalpostIdLøser klarte ikke lagre journalpostId $journalpostId for transaksjonsId ${behov.uuid()}", ex)
            }
        }
    }
}
