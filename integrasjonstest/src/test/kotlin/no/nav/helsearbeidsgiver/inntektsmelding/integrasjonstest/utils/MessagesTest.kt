package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersisterImLøsning
import no.nav.helsearbeidsgiver.utils.json.toJson

private const val UNEXPECTED_NULL_ERROR_MSG = "Expected value to not be null, but was null."

class MessagesTest : FunSpec({

    test("skal finne message for event") {
        val funnetMelding = Mock.meldingerMedLoesning.find(EventName.INNTEKTSMELDING_JOURNALFOERT)

        funnetMelding.shouldNotBeNull()
    }

    test("skal ikke finne message for event") {
        val e = shouldThrowExactly<AssertionError> {
            Mock.meldingerMedLoesning.find(EventName.FORESPØRSEL_MOTTATT)
        }

        e.message shouldBe UNEXPECTED_NULL_ERROR_MSG
    }

    test("skal finne message for behov") {
        val funnetMeldingMedTilgangskontroll = Mock.meldingerMedLoesning.find(EventName.INNTEKTSMELDING_JOURNALFOERT, BehovType.TILGANGSKONTROLL)

        funnetMeldingMedTilgangskontroll.shouldNotBeNull()

        val funnetMeldingMedArbeidsgivere = Mock.meldingerMedLoesning.find(EventName.INNTEKTSMELDING_JOURNALFOERT, BehovType.ARBEIDSGIVERE)

        funnetMeldingMedArbeidsgivere.shouldNotBeNull()
    }

    test("skal ikke finne message for behov") {
        val e = shouldThrowExactly<AssertionError> {
            Mock.meldingerMedLoesning.find(EventName.INNTEKTSMELDING_JOURNALFOERT, BehovType.HENT_IM_ORGNR)
        }

        e.message shouldBe UNEXPECTED_NULL_ERROR_MSG
    }

    test("skal finne message for løsning") {
        val funnetMelding = Mock.meldingerMedLoesning.find(EventName.INNTEKTSMELDING_JOURNALFOERT, BehovType.TILGANGSKONTROLL, loesning = true)

        funnetMelding.shouldNotBeNull()
    }

    test("skal ikke finne message for løsning") {
        val funnetMelding = Mock.meldingerMedLoesning.find(EventName.INNTEKTSMELDING_JOURNALFOERT, BehovType.TILGANGSKONTROLL, loesning = false)

        funnetMelding.shouldNotBeNull()
    }

    test("skal ikke finne message når løsning kreves") {
        val e = shouldThrowExactly<AssertionError> {
            Mock.meldingerUtenLoesning.find(EventName.INNTEKTSMELDING_JOURNALFOERT, BehovType.TILGANGSKONTROLL, loesning = true)
        }

        e.message shouldBe UNEXPECTED_NULL_ERROR_MSG
    }
})

private object Mock {
    private val imJournalfoertUtenLoesning = mapOf(
        Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_JOURNALFOERT.toJson(),
        Key.BEHOV.str to listOf(BehovType.TILGANGSKONTROLL, BehovType.ARBEIDSGIVERE).toJson()
    ).toJson()

    private val oppgaveOpprettetUtenLoesning = mapOf(
        Key.EVENT_NAME.str to EventName.OPPGAVE_OPPRETTET.toJson(),
        Key.BEHOV.str to listOf(BehovType.ENDRE_SAK_STATUS, BehovType.ENDRE_OPPGAVE_STATUS).toJson()
    ).toJson()

    private val imJournalfoertMedLoesninger = mapOf(
        Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_JOURNALFOERT.toJson(),
        Key.BEHOV.str to listOf(BehovType.TILGANGSKONTROLL, BehovType.ARBEIDSGIVERE).toJson(),
        Key.LØSNING.str to mapOf(
            BehovType.ARBEIDSGIVERE to PersisterImLøsning("ghi"),
            BehovType.TILGANGSKONTROLL to PersisterImLøsning("ghi")
        ).toJson()
    ).toJson()

    val meldingerUtenLoesning = Messages(mutableListOf(imJournalfoertUtenLoesning, oppgaveOpprettetUtenLoesning))
    val meldingerMedLoesning = Messages(mutableListOf(imJournalfoertMedLoesninger))

    private fun EventName.toJson(): JsonElement =
        toJson(EventName.serializer())

    private fun List<BehovType>.toJson(): JsonElement =
        toJson(BehovType.serializer())

    private fun Map<BehovType, PersisterImLøsning>.toJson(): JsonElement =
        toJson(
            MapSerializer(
                BehovType.serializer(),
                PersisterImLøsning.serializer()
            )
        )
}
