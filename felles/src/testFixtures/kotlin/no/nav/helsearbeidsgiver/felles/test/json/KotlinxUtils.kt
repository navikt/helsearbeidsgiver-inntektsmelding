package no.nav.helsearbeidsgiver.felles.test.json

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toMap

fun JsonElement.lesBehov(): BehovType? = Key.BEHOV.lesOrNull(BehovType.serializer(), this.toMap())
