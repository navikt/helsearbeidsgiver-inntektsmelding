package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import org.junit.jupiter.api.Test
import java.util.UUID

class InnsendingStartetIT : EndToEndTest() {

    @Test
    fun `Test at innsnending er mottatt`() {
        val uuid = UUID.randomUUID().toString()
        publish(
            JsonMessage.newMessage(
                mapOf(
                    Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_REQUESTED,
                    Key.UUID.str to uuid,
                    Key.INNTEKTSMELDING.str to GYLDIG
                )
            ).toString()
        )
        //  1) vi sender en InntektsMeldingStartet event
        //  2) vi kjører Prossessoren ,med avhengigheter breg og arbeidsforhold, postgres, redis
        //  3) InntektMeldingMottatt
        //
    }
}
