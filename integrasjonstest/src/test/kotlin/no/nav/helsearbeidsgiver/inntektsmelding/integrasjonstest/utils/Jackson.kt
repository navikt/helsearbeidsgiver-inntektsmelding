package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.inntektsmelding.felles.models.InnsendingRequest
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.utils.json.parseJson

object Jackson {
    private val objectMapper = customObjectMapper()

    fun toJson(innsendingRequest: InnsendingRequest): JsonElement =
        objectMapper.writeValueAsString(innsendingRequest).parseJson()
}
