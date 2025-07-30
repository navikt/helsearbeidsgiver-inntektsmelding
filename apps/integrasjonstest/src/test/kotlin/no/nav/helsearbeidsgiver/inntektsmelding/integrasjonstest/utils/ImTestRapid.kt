package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils

import com.github.navikt.tbd_libs.rapids_and_rivers_api.FailedMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.OutgoingMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.github.navikt.tbd_libs.rapids_and_rivers_api.SentMessage
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.serialization.json.JsonObject
import no.nav.helsearbeidsgiver.utils.json.parseJson

class ImTestRapid : RapidsConnection() {
    internal val messages = Messages()

    override fun publish(message: String) {
        publishSafe(message)
    }

    override fun publish(
        key: String,
        message: String,
    ) {
        publishSafe(message)
    }

    override fun publish(messages: List<OutgoingMessage>): Pair<List<SentMessage>, List<FailedMessage>> {
        messages.forEach {
            publishSafe(it.body)
        }

        return Pair(emptyList(), emptyList())
    }

    override fun rapidName(): String = "imTestRapid"

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

        notifyMessage(
            message = message,
            context = this,
            metadata = MessageMetadata("test.message", -1, -1, null, emptyMap()),
            metrics = SimpleMeterRegistry(),
        )
    }
}

class JsonObjectRequired(
    val json: String,
) : Exception("Message must be a JSON-object, but wasn't: $json")
