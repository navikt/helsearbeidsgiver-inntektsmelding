package no.nav.helsearbeidsgiver.inntektsmelding.api.arbeidsgiver

import io.ktor.server.routing.get
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.altinn.AltinnOrganisasjon
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.JsonAliasArbeidsgivere
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.loeser.LøsningFailure
import no.nav.helsearbeidsgiver.felles.loeser.LøsningSuccess
import no.nav.helsearbeidsgiver.inntektsmelding.api.logger
import no.nav.helsearbeidsgiver.inntektsmelding.api.sikkerlogg
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.RouteExtra
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondInternalServerError
import no.nav.helsearbeidsgiver.inntektsmelding.api.utils.respondOk

fun RouteExtra.ArbeidsgiverRoute() {
    route.get("/arbeidsgivere") {
        val id = connection.publiser(
            // TODO Behov må være liste. Dette bør endres i Akkumulatoren.
            Key.BEHOV to listOf(BehovType.ARBEIDSGIVERE)
        )

        val løsning = redis.hent<Result>(id)

        when (løsning) {
            is LøsningSuccess ->
                respondOk(løsning.resultat.arbeidsgivere)
            is LøsningFailure ->
                respondInternalServerError(løsning.feilmelding)
        }
    }
}

private data class Result(
    @JsonAliasArbeidsgivere
    val arbeidsgivere: Set<AltinnOrganisasjon>
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
