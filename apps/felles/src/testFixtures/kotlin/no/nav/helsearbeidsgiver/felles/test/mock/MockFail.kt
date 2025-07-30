package no.nav.helsearbeidsgiver.felles.test.mock

import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.model.Fail
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.json.toJson
import java.util.UUID

fun mockFail(
    feilmelding: String,
    eventName: EventName,
    kontekstId: UUID = UUID.randomUUID(),
    behovType: BehovType? = null,
): Fail =
    Fail(
        feilmelding = feilmelding,
        kontekstId = kontekstId,
        utloesendeMelding =
            mapOf(
                Key.EVENT_NAME to eventName.toJson(),
                Key.KONTEKST_ID to kontekstId.toJson(),
                Key.BEHOV to behovType?.toJson(),
            ).mapValuesNotNull { it },
    )
