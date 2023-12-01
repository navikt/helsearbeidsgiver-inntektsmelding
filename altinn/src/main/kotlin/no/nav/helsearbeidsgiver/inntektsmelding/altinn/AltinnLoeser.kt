package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.altinn.AltinnOrganisasjon
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.loeser.ObjectRiver
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.metrics.recordTime
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

data class Melding(
    val eventName: EventName,
    val transactionId: UUID,
    val behovType: BehovType,
    val identitetsnummer: String
)

class AltinnLoeser(
    private val altinnClient: AltinnClient
) : ObjectRiver<Melding>() {

    override fun les(json: Map<IKey, JsonElement>): Melding =
        Melding(
            eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
            transactionId = Key.UUID.les(UuidSerializer, json),
            behovType = Key.BEHOV.krev(BehovType.ARBEIDSGIVERE, BehovType.serializer(), json),
            identitetsnummer = Key.IDENTITETSNUMMER.les(String.serializer(), json)
        )

    override fun Melding.haandter(): Map<IKey, JsonElement> {
        val rettigheter = Metrics.altinnRequest.recordTime {
            altinnClient.hentRettighetOrganisasjoner(identitetsnummer)
        }
        val rettigheterForenklet = rettigheter.filter { it.orgnr != null && it.orgnrHovedenhet != null }.map { it.orgnr!! }.toSet()
        return mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.UUID to transactionId.toJson(),
            Key.DATA to "".toJson(),
            Key.ORG_RETTIGHETER to rettigheter.toJson(AltinnOrganisasjon.serializer().set()),
            Key.ORG_RETTIGHETER_FORENKLET to rettigheterForenklet.toJson(String.serializer().set())
        )
    }
}
