package no.nav.helsearbeidsgiver.inntektsmelding.api.arbeidsgivere

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import io.ktor.server.routing.get
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helsearbeidsgiver.altinn.AltinnOrganisasjon
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import no.nav.helsearbeidsgiver.felles.loeser.Løsning
import no.nav.helsearbeidsgiver.felles.loeser.Løsning.Companion.toLøsning
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerlogg
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.identitetsnummer
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondOk

fun RouteExtra.ArbeidsgivereRoute() {
    route.get(Routes.ARBEIDSGIVERE) {
        val messageId = connection.publish(
            // TODO Behov må være liste. Dette bør endres i Akkumulatoren.
            Key.BEHOV to listOf(BehovType.ARBEIDSGIVERE).toJson(BehovType::toJson),
            Key.IDENTITETSNUMMER to identitetsnummer().toJson(),
            block = ::loggPublisert
        )

        val løsning = redis.hent(messageId).toLøsning()

        when (løsning) {
            is Løsning.Success ->
                respondOk(løsning.resultat)
            is Løsning.Failure ->
                respondInternalServerError(løsning.feilmelding)
        }
    }
}

private fun JsonNode.toLøsning(): Løsning<Set<AltinnOrganisasjon>> =
    toJsonElement()
        .fromJson<Map<BehovType, JsonElement>>()
        .get(BehovType.ARBEIDSGIVERE)
        ?.toLøsning<Set<AltinnOrganisasjon>, _>()
        ?: throw ArbeidsgivereJsonMismatchedInputException("Fant ikke behov.")

private fun loggPublisert(message: JsonMessage) {
    "Publiserte til rapid. id=${message.id}".let {
        logger.info(it)
        sikkerlogg.info("$it json=${message.toJson()}")
    }
}

private class ArbeidsgivereJsonMismatchedInputException(message: String) : MismatchedInputException(null, message)
