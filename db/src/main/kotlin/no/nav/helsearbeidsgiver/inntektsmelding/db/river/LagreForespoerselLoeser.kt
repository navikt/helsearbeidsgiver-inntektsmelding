package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.inntektsmelding.db.ForespoerselRepository

class LagreForespoerselLoeser(rapidsConnection: RapidsConnection, private val repository: ForespoerselRepository) : Loeser(rapidsConnection) {

    override fun accept(): River.PacketValidation {
        return River.PacketValidation {
            it.demandValue(Key.BEHOV.str, BehovType.LAGRE_FORESPOERSEL.name)
            it.requireKey(DataFelt.ORGNRUNDERENHET.str)
            it.requireKey(Key.IDENTITETSNUMMER.str)
            it.requireKey(Key.FORESPOERSEL_ID.str)
        }
    }

    override fun onBehov(behov: Behov) {
        val orgnr = behov[DataFelt.ORGNRUNDERENHET].asText()
        val fnr = behov[Key.IDENTITETSNUMMER].asText()
        repository.lagreForespoersel(behov.forespoerselId!!, orgnr)

        behov.createEvent(
            EventName.FORESPØRSEL_LAGRET,
            mapOf(
                Key.IDENTITETSNUMMER to fnr,
                DataFelt.ORGNRUNDERENHET to orgnr
            )
        ).also { publishEvent(it) }
    }
}
