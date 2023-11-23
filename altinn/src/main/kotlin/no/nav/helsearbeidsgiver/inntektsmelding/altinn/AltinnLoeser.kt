package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.altinn.AltinnClient
import no.nav.helsearbeidsgiver.altinn.AltinnOrganisasjon
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.IKey
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.loeser.ObjectRiver
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.metrics.recordTime
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson

data class Melding(
    val behovType: BehovType,
    val identitetsnummer: String
)

class AltinnLoeser(
    private val altinnClient: AltinnClient
) : ObjectRiver<Melding>() {
    init {
        start()
    }

    override fun les(json: Map<IKey, JsonElement>): Melding =
        Melding(
            behovType = Key.BEHOV.krev(BehovType.ARBEIDSGIVERE, BehovType.serializer(), json),
            identitetsnummer = Key.IDENTITETSNUMMER.les(String.serializer(), json)
        )

    override fun Melding.haandter(): Map<IKey, JsonElement> {
        val rettigheter = Metrics.altinnRequest.recordTime {
            altinnClient.hentRettighetOrganisasjoner(identitetsnummer)
        }

        return mapOf(
            Key.BEHOV to behovType.toJson(),
            Key.DATA to "".toJson(),
            DataFelt.ORG_RETTIGHETER to rettigheter.toJson(AltinnOrganisasjon.serializer().set())
        )
    }
}
