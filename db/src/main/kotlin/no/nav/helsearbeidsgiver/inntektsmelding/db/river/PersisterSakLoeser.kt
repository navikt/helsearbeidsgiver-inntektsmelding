package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.serialization.builtins.serializer
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.Loeser
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Behov
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.publishData
import no.nav.helsearbeidsgiver.inntektsmelding.db.ForespoerselRepository
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class PersisterSakLoeser(
    rapidsConnection: RapidsConnection,
    private val repository: ForespoerselRepository,
) : Loeser(rapidsConnection) {
    private val sikkerLogger = sikkerLogger()

    override fun accept(): River.PacketValidation =
        River.PacketValidation {
            it.demandValue(Key.BEHOV.str, BehovType.PERSISTER_SAK_ID.name)
            it.requireKey(Key.FORESPOERSEL_ID.str)
            it.requireKey(Key.SAK_ID.str)
        }

    override fun onBehov(behov: Behov) {
        val json =
            behov.jsonMessage
                .toJson()
                .parseJson()
                .toMap()

        val transaksjonId = Key.UUID.les(UuidSerializer, json)
        val sakId = Key.SAK_ID.les(String.serializer(), json)

        val forespoerselId = behov.forespoerselId!!.let(UUID::fromString)

        sikkerLogger.info("PersisterSakLøser mottok behov med transaksjonId: $transaksjonId")

        repository.oppdaterSakId(forespoerselId.toString(), sakId)

        sikkerLogger.info("PersisterSakLøser lagred sakId: $sakId for forespoerselId: $forespoerselId")

        rapidsConnection.publishData(
            eventName = behov.event,
            transaksjonId = transaksjonId,
            forespoerselId = forespoerselId,
            Key.PERSISTERT_SAK_ID to sakId.toJson(),
        )
    }
}
