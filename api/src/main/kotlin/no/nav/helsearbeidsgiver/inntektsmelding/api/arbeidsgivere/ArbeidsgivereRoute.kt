package no.nav.helsearbeidsgiver.inntektsmelding.api.arbeidsgivere

import io.ktor.server.routing.get
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helsearbeidsgiver.altinn.AltinnOrganisasjon
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.løsning
import no.nav.helsearbeidsgiver.felles.loeser.Løsning
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.inntektsmelding.api.Routes
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerLogger
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.identitetsnummer
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondOk
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson

fun RouteExtra.arbeidsgivereRoute() {
    route.get(Routes.ARBEIDSGIVERE) {
        val messageId = connection.publish(
            // TODO Behov må være liste. Dette bør endres i Akkumulatoren.
            Key.BEHOV to listOf(BehovType.ARBEIDSGIVERE).toJson(BehovType.serializer()),
            Key.IDENTITETSNUMMER to identitetsnummer().toJson(),
            block = ::loggPublisert
        )

        val løsning = redis.hent(messageId).toLøsning()

        when (løsning) {
            is Løsning.Success ->
                respondOk(løsning.resultat, AltinnOrganisasjon.serializer().set())
            is Løsning.Failure ->
                respondInternalServerError(løsning.feilmelding, String.serializer())
        }
    }
}

private fun JsonElement.toLøsning(): Løsning<Set<AltinnOrganisasjon>> =
    fromJson(
        MapSerializer(
            BehovType.serializer(),
            AltinnOrganisasjon.serializer().set().løsning()
        )
    )
        .get(BehovType.ARBEIDSGIVERE)
        ?: throw ArbeidsgivereJsonException("Fant ikke nødvendig løsning i JSON fra Redis.")

private fun loggPublisert(message: JsonMessage) {
    "Publiserte til rapid. id=${message.id}".let {
        logger.info(it)
        sikkerLogger.info("$it json=${message.toJson()}")
    }
}

private class ArbeidsgivereJsonException(message: String) : IllegalStateException(message)
