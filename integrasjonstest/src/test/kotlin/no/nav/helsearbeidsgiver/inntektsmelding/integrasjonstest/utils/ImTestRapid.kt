package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils

import kotlinx.serialization.json.JsonObject
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.utils.json.parseJson

class ImTestRapid : RapidsConnection() {

    internal val messages = Messages()

    override fun publish(message: String) {
        publishSafe(message)
    }

    override fun publish(key: String, message: String) {
        publishSafe(message)
    }

    override fun rapidName(): String =
        "imTestRapid"

    override fun start() {}
    override fun stop() {}

    internal fun reset() {
        messages.reset()
    }

    private fun publishSafe(message: String) {
        // Rapid tåler bare JSON-objekt
        if (message.parseJson() !is JsonObject) {
            throw JsonObjectRequired(message)
        }

        messages.add(message)

        println("Rapid: $message")

        notifyMessage(message, this)
    }
}

class JsonObjectRequired(val json: String) : Exception("Message must be a JSON-object, but wasn't: $json")
