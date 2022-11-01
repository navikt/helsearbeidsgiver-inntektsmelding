package no.nav.helsearbeidsgiver.inntektsmelding.api.arbeidsgivere

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.routing.post
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helsearbeidsgiver.altinn.AltinnOrganisasjon
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.loeser.Løsning
import no.nav.helsearbeidsgiver.felles.loeser.LøsningFailure
import no.nav.helsearbeidsgiver.felles.loeser.LøsningSuccess
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerlogg
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondOk

private val objectMapper = customObjectMapper()

fun RouteExtra.ArbeidsgivereRoute() {
    route.post("/arbeidsgivere") {
        val request = call.receive<ArbeidsgivereRequest>()

        val id = connection.publiser(
            // TODO Behov må være liste. Dette bør endres i Akkumulatoren.
            Key.BEHOV to listOf(BehovType.ARBEIDSGIVERE),
            Key.IDENTITETSNUMMER to request.identitetsnummer
        )

        val resultat = redis.hent(id).tilResultat()

        when (resultat.arbeidsgivere) {
            is LøsningSuccess ->
                respondOk(resultat.arbeidsgivere.resultat)
            is LøsningFailure ->
                respondInternalServerError(resultat.arbeidsgivere.feilmelding)
        }
    }
}

private data class ArbeidsgivereRequest(
    val identitetsnummer: String
)

private data class Resultat(
    val arbeidsgivere: Løsning<Set<AltinnOrganisasjon>>
)

private fun RapidsConnection.publiser(vararg eventFields: Pair<Key, Any>): String {
    val event = eventFields.toMap()
        .mapKeys { (key, _) -> key.str }
        .let(JsonMessage::newMessage)

    this.publish(event.id, event.toJson())

    "Publiserte til rapid. id=${event.id}".let {
        logger.info(it)
        sikkerlogg.info("$it json=${event.toJson()}")
    }

    return event.id
}

// TODO undersøk en mer elegant/generell måte å gjøre dette på
private fun JsonNode.tilResultat(): Resultat =
    this.get(BehovType.ARBEIDSGIVERE.name)
        ?.orNull()
        ?.let { løsningJson ->
            val løsningSuccess = løsningJson.get("resultat")
                ?.orNull()
                ?.let { setJson ->
                    setJson.map { objectMapper.convertValue(it, AltinnOrganisasjon::class.java) }.toSet()
                }
                ?.let(::LøsningSuccess)

            val løsningFailure = løsningJson.get("feilmelding")
                ?.orNull()
                ?.asText()
                ?.let(::LøsningFailure)

            løsningSuccess
                ?: løsningFailure
                ?: throw ArbeidsgivereJsonMismatchedInputException("Fant ikke løsning.")
        }
        ?.let(::Resultat)
        ?: throw ArbeidsgivereJsonMismatchedInputException("Fant ikke behov.")

private fun JsonNode?.orNull(): JsonNode? =
    this?.takeUnless(JsonNode::isMissingOrNull)

private class ArbeidsgivereJsonMismatchedInputException(message: String) : MismatchedInputException(null, message)
