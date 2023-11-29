package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.inntektsmelding.db.ForespoerselRepository
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

class PersisterSakLoeser(
    rapidsConnection: RapidsConnection,
    private val repository: ForespoerselRepository
) : Loeser(rapidsConnection) {
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandValue(Key.BEHOV.str, BehovType.PERSISTER_SAK_ID.name)
            it.requireKey(Key.FORESPOERSEL_ID.str)
            it.requireKey(Key.SAK_ID.str)
        }
    }

    override fun onBehov(behov: Behov) {
        sikkerLogger.info("PersisterSakLøser mottok behov med uuid: ${behov.uuid()}")
        val sakId = behov[Key.SAK_ID].asText()
        repository.oppdaterSakId(behov.forespoerselId!!, sakId)
        sikkerLogger.info("PersisterSakLøser lagred sakId: $sakId for forespoerselId: ${behov.forespoerselId}")
        behov.createData(mapOf(Key.PERSISTERT_SAK_ID to sakId)).also { publishData(it) }
    }
}
