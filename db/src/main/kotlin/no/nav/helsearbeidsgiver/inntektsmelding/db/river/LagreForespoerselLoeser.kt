package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.publishEvent
import no.nav.helsearbeidsgiver.inntektsmelding.db.ForespoerselRepository
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

class LagreForespoerselLoeser(
    rapidsConnection: RapidsConnection,
    private val repository: ForespoerselRepository,
) : Loeser(rapidsConnection) {
    override fun accept(): River.PacketValidation =
        River.PacketValidation {
            it.demandValue(Key.BEHOV.str, BehovType.LAGRE_FORESPOERSEL.name)
            it.requireKey(Key.ORGNRUNDERENHET.str)
            it.requireKey(Key.IDENTITETSNUMMER.str)
            it.requireKey(Key.FORESPOERSEL_ID.str)
        }

    override fun onBehov(behov: Behov) {
        val forespoerselId = behov.forespoerselId!!.let(UUID::fromString)
        val orgnr = behov[Key.ORGNRUNDERENHET].asText()
        val fnr = behov[Key.IDENTITETSNUMMER].asText()

        repository.lagreForespoersel(forespoerselId.toString(), orgnr)

        rapidsConnection.publishEvent(
            eventName = EventName.FORESPØRSEL_LAGRET,
            transaksjonId = null,
            forespoerselId = forespoerselId,
            Key.IDENTITETSNUMMER to fnr.toJson(),
            Key.ORGNRUNDERENHET to orgnr.toJson(),
        )
    }
}
