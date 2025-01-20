package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.altinn.AltinnOrganisasjon
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.test.mock.mockFail
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

object Mock {
    fun innkommendeMelding(): Melding {
        val fnr = Fnr.genererGyldig()
        val svarKafkaKey = KafkaKey(fnr)

        return Melding(
            eventName = EventName.AKTIVE_ORGNR_REQUESTED,
            behovType = BehovType.ARBEIDSGIVERE,
            transaksjonId = UUID.randomUUID(),
            data =
                mapOf(
                    Key.SVAR_KAFKA_KEY to svarKafkaKey.toJson(),
                    Key.ARBEIDSGIVER_FNR to fnr.toJson(),
                ),
            svarKafkaKey = svarKafkaKey,
            fnr = fnr,
        )
    }

    fun Melding.toMap(): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to behovType.toJson(),
            Key.KONTEKST_ID to transaksjonId.toJson(),
            Key.DATA to data.toJson(),
        )

    val fail = mockFail("One does not simply walk into Mordor.", EventName.AKTIVE_ORGNR_REQUESTED)

    val altinnOrganisasjoner =
        setOf(
            AltinnOrganisasjon(
                navn = "Pippin's Breakfast & Breakfast",
                type = "gluttonous",
            ),
        )
}
