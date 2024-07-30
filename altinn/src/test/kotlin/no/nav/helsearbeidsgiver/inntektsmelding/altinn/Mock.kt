package no.nav.helsearbeidsgiver.inntektsmelding.altinn

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import no.nav.helsearbeidsgiver.altinn.AltinnOrganisasjon
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

object Mock {
    fun innkommendeMelding(): Melding {
        val fnr = Fnr.genererGyldig()

        return Melding(
            eventName = EventName.AKTIVE_ORGNR_REQUESTED,
            behovType = BehovType.ARBEIDSGIVERE,
            transaksjonId = UUID.randomUUID(),
            data =
                mapOf(
                    Key.ARBEIDSGIVER_FNR to fnr.toJson(),
                ),
            fnr = fnr,
        )
    }

    fun Melding.toMap(): Map<Key, JsonElement> =
        mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to behovType.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.ARBEIDSGIVER_FNR to fnr.toJson(),
                ).toJson(),
        )

    val fail =
        Fail(
            feilmelding = "One does not simply walk into Mordor.",
            event = EventName.AKTIVE_ORGNR_REQUESTED,
            transaksjonId = UUID.randomUUID(),
            forespoerselId = null,
            utloesendeMelding = JsonNull,
        )

    val altinnOrganisasjoner =
        setOf(
            AltinnOrganisasjon(
                navn = "Pippin's Breakfast & Breakfast",
                type = "gluttonous",
            ),
        )
}
