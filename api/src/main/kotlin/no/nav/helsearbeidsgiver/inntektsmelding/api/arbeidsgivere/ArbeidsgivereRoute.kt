package no.nav.helsearbeidsgiver.inntektsmelding.api.arbeidsgivere

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import io.ktor.server.routing.get
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helsearbeidsgiver.altinn.AltinnOrganisasjon
import no.nav.helsearbeidsgiver.felles.ApiBehov
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Identitetsnummer
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.løsning
import no.nav.helsearbeidsgiver.felles.json.set
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.loeser.Løsning
import no.nav.helsearbeidsgiver.felles.message.Plan
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
        val plan = Plan(
            setOf(BehovType.ARBEIDSGIVERE)
        )

        val input = ApiBehov.Input.Arbeidsgivere(
            identitetsnummer = identitetsnummer().let(::Identitetsnummer)
        )
            // nødvendig for serialisering
            .let { it as ApiBehov.Input }

        // TODO sende plan direkte? men hvordan knytte bestemt plan til bestemt input?
        val messageId = connection.publish(
            Key.BEHOV to ApiBehov(plan, input).toJson(ApiBehov.serializer(ApiBehov.Input.serializer())),
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

private fun JsonElement.toLøsning(): Løsning<Set<AltinnOrganisasjon>> =
    fromJson(
        MapSerializer(
            BehovType.serializer(),
            AltinnOrganisasjon.serializer().set().løsning()
        )
    )
        .get(BehovType.ARBEIDSGIVERE)
        ?: throw ArbeidsgivereJsonMismatchedInputException("Fant ikke behov.")

private fun loggPublisert(message: JsonMessage) {
    "Publiserte til rapid. id=${message.id}".let {
        logger.info(it)
        sikkerlogg.info("$it json=${message.toJson()}")
    }
}

private class ArbeidsgivereJsonMismatchedInputException(message: String) : MismatchedInputException(null, message)
