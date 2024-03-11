package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils

import kotlinx.serialization.json.JsonObject
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.utils.json.parseJson

class ImTestRapid : RapidsConnection() {
    private val messages = mutableListOf<Pair<String?, String>>()

    override fun publish(message: String) {
        publishSafe(null, message)
    }

    override fun publish(key: String, message: String) {
        publishSafe(key, message)
    }

    override fun rapidName(): String =
        "imTestRapid"

    override fun start() {}
    override fun stop() {}

    internal fun reset() {
        messages.clear()
    }

    private fun publishSafe(key: String?, message: String) {
        // Rapid tåler bare JSON-objekt
        if (message.parseJson() !is JsonObject) {
            throw JsonObjectRequired(message)
        }

        notifyMessage(message, this)
        messages.add(key to message)
    }
}

class JsonObjectRequired(val json: String) : Exception("Message must be a JSON-object, but wasn't: $json")
