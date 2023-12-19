package no.nav.helsearbeidsgiver.inntektsmelding.feilbehandler.river

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.utils.mapOfNotNull
import no.nav.helsearbeidsgiver.utils.json.parseJson
import java.util.UUID

class FeilLytterTest : FunSpec({

    val handler = FeilLytter.FeilHaandterer

    test("skal håndtere gyldige feil med spesifiserte behov") {

        handler.behovSomHaandteres.forEach { handler.skalHaandteres(lagGyldigFeil(it)) shouldBe true }
    }

    test("skal ignorere gyldige feil med visse behov") {
        val ignorerteBehov = BehovType.entries.filterNot { handler.behovSomHaandteres.contains(it) }
        ignorerteBehov.forEach { handler.skalHaandteres(lagGyldigFeil(it)) shouldBe false }
    }

    test("skal ignorere feil uten behov") {
        val uuid = UUID.randomUUID()
        val feil = lagGyldigFeil(BehovType.JOURNALFOER).copy(
            utloesendeMelding =
            JsonMessage.newMessage(
                mapOfNotNull(
                    Key.UUID.str to uuid,
                    Key.FORESPOERSEL_ID.str to uuid
                )
            ).toJson().parseJson()
        )
        handler.skalHaandteres(feil) shouldBe false
    }

    test("skal ignorere feil uten forespørselId") {
        val feil = lagGyldigFeil(BehovType.JOURNALFOER).copy(forespoerselId = null)
        handler.skalHaandteres(feil) shouldBe false
    }
})

fun lagGyldigFeil(it: BehovType): Fail {
    val uuid = UUID.randomUUID()
    val jsonMessage = JsonMessage.newMessage(
        EventName.OPPGAVE_OPPRETT_REQUESTED.name,
        mapOfNotNull(
            Key.BEHOV.str to it,
            Key.UUID.str to uuid,
            Key.FORESPOERSEL_ID.str to uuid
        )
    )
    return Fail("Feil", EventName.OPPGAVE_OPPRETT_REQUESTED, UUID.randomUUID(), UUID.randomUUID(), jsonMessage.toJson().parseJson())
}
