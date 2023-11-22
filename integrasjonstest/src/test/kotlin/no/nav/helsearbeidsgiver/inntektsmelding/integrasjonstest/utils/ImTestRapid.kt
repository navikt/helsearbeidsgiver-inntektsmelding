package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils

import no.nav.helse.rapids_rivers.RapidsConnection

class ImTestRapid : RapidsConnection() {
    private val messages = mutableListOf<Pair<String?, String>>()
    fun reset() {
        messages.clear()
    }
    override fun publish(message: String) {
        notifyMessage(message, this)
        messages.add(null to message)
    }
    override fun publish(key: String, message: String) {
        notifyMessage(message, this)
        messages.add(key to message)
    }
    override fun rapidName(): String {
        return "testRapid"
    }
    override fun start() {}
    override fun stop() {}
}
