package no.nav.helsearbeidsgiver.felles.test.json

import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.utils.json.fromJson

fun JsonElement.readFail(): Fail =
    toMap()[Key.FAIL].shouldNotBeNull().fromJson(Fail.serializer())
