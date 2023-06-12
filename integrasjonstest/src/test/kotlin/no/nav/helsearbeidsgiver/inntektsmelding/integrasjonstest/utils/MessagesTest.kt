package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils

import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.NavnLøsning
import no.nav.helsearbeidsgiver.felles.PersonDato
import no.nav.helsearbeidsgiver.felles.Tilgang
import no.nav.helsearbeidsgiver.felles.TilgangskontrollLøsning
import no.nav.helsearbeidsgiver.felles.test.date.juli
import no.nav.helsearbeidsgiver.felles.test.json.fromJsonMapOnlyKeys
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJson

private const val UNEXPECTED_NULL_ERROR_MSG = "Expected value to not be null, but was null."

class MessagesTest : FunSpec({

    test("finner korrekt melding for event") {
        val expectedEventName = EventName.HENT_PREUTFYLT

        val funnetMelding = Mock.meldingerMedBehovMedLoesning.find(
            event = expectedEventName,
            behovType = null,
            dataFelt = null,
            maaHaLoesning = false
        )

        val actualEventName = funnetMelding.fromJsonMapOnlyKeys()[Key.EVENT_NAME]?.fromJson(EventName.serializer())

        actualEventName shouldBe expectedEventName
    }

    test("feiler når ikke finner melding for event") {
        val e = shouldThrowExactly<AssertionError> {
            Mock.meldingerMedBehovMedLoesning.find(
                event = EventName.FORESPØRSEL_MOTTATT,
                behovType = null,
                dataFelt = null,
                maaHaLoesning = false
            )
        }

        e.message shouldBe UNEXPECTED_NULL_ERROR_MSG
    }

    context("finner korrekt melding for event og behov") {
        withData(
            BehovType.TILGANGSKONTROLL,
            BehovType.FULLT_NAVN
        ) { expectedBehovType ->
            val expectedEventName = EventName.HENT_PREUTFYLT

            val funnetMelding = Mock.meldingerMedBehovMedLoesning.find(
                event = expectedEventName,
                behovType = expectedBehovType,
                dataFelt = null,
                maaHaLoesning = false
            )

            funnetMelding.fromJsonMapOnlyKeys().let {
                it[Key.EVENT_NAME]?.fromJson(EventName.serializer()) shouldBe expectedEventName

                val behovJson = it[Key.BEHOV].shouldNotBeNull()

                behovJson.fromJson(BehovType.serializer().list()) shouldContain expectedBehovType
            }
        }
    }

    test("feiler når ikke finner melding for event og behov") {
        val e = shouldThrowExactly<AssertionError> {
            Mock.meldingerMedBehovMedLoesning.find(
                event = EventName.HENT_PREUTFYLT,
                behovType = BehovType.HENT_IM_ORGNR,
                dataFelt = null,
                maaHaLoesning = false
            )
        }

        e.message shouldBe UNEXPECTED_NULL_ERROR_MSG
    }

    test("finner korrekt melding for event og behov, med løsning") {
        val expectedEventName = EventName.HENT_PREUTFYLT
        val expectedBehovType = BehovType.TILGANGSKONTROLL

        val funnetMelding = Mock.meldingerMedBehovMedLoesning.find(
            event = expectedEventName,
            behovType = expectedBehovType,
            dataFelt = null,
            maaHaLoesning = true
        )

        funnetMelding.fromJsonMapOnlyKeys().let {
            it[Key.EVENT_NAME]?.fromJson(EventName.serializer()) shouldBe expectedEventName

            val behovJson = it[Key.BEHOV].shouldNotBeNull()

            behovJson.fromJson(BehovType.serializer().list()) shouldContain expectedBehovType

            val loesning = it.lesLoesning(expectedBehovType, TilgangskontrollLøsning.serializer())

            loesning?.error.shouldBeNull()
            loesning?.value shouldBe Tilgang.HAR_TILGANG
        }
    }

    test("feiler når ikke finner melding for event og behov, med løsning") {
        val e = shouldThrowExactly<AssertionError> {
            Mock.meldingerMedBehovUtenLoesning.find(
                event = EventName.HENT_PREUTFYLT,
                behovType = BehovType.TILGANGSKONTROLL,
                dataFelt = null,
                maaHaLoesning = true
            )
        }

        e.message shouldBe UNEXPECTED_NULL_ERROR_MSG
    }

    test("finner korrekt melding for event og datafelt") {
        val expectedEventName = EventName.HENT_PREUTFYLT

        val funnetMelding = Mock.meldingerMedDatafelt.find(
            event = expectedEventName,
            behovType = null,
            dataFelt = DataFelt.VIRKSOMHET,
            maaHaLoesning = false
        )

        funnetMelding.fromJsonMapOnlyKeys().let {
            it[Key.EVENT_NAME]?.fromJson(EventName.serializer()) shouldBe expectedEventName

            it shouldContainKey Key.DATA
        }

        funnetMelding.fromJsonMapFiltered(DataFelt.serializer()).let {
            it[DataFelt.VIRKSOMHET]?.fromJson(String.serializer()) shouldBe Mock.ORGNR
        }
    }

    test("feiler når ikke finner melding for event og datafelt") {
        val e = shouldThrowExactly<AssertionError> {
            Mock.meldingerMedDatafelt.find(
                event = EventName.HENT_PREUTFYLT,
                behovType = null,
                dataFelt = DataFelt.ARBEIDSFORHOLD,
                maaHaLoesning = true
            )
        }

        e.message shouldBe UNEXPECTED_NULL_ERROR_MSG
    }
})

private object Mock {
    const val ORGNR = "orgnr-pai"

    val meldingerMedBehovUtenLoesning = basisfelt().toJson().toMessages()
    val meldingerMedBehovMedLoesning = basisfelt().plus(loesninger()).toJson().toMessages()
    val meldingerMedDatafelt = basisfelt().plus(datafelt()).toJson().toMessages()

    private fun basisfelt(): Map<String, JsonElement> =
        mapOf(
            Key.EVENT_NAME.str to EventName.HENT_PREUTFYLT.toJson(EventName.serializer()),
            Key.BEHOV.str to listOf(
                BehovType.TILGANGSKONTROLL,
                BehovType.FULLT_NAVN,
                BehovType.VIRKSOMHET
            ).toJson(BehovType.serializer()),
            Key.DATA.str to "".toJson()
        )

    private fun loesninger(): Pair<String, JsonElement> =
        Pair(
            Key.LØSNING.str,
            mapOf(
                BehovType.TILGANGSKONTROLL to TilgangskontrollLøsning(Tilgang.HAR_TILGANG).toJson(TilgangskontrollLøsning.serializer()),
                BehovType.FULLT_NAVN to NavnLøsning(
                    PersonDato(
                        navn = "Thomas Toget",
                        fødselsdato = 11.juli
                    )
                ).toJson(NavnLøsning.serializer())
            ).toJson()
        )

    private fun datafelt(): Map<String, JsonElement> =
        mapOf(
            DataFelt.VIRKSOMHET.str to ORGNR.toJson()
        )

    private fun JsonElement.toMessages(): Messages =
        Messages(mutableListOf(this))

    private fun Map<BehovType, JsonElement>.toJson(): JsonElement =
        toJson(
            MapSerializer(
                BehovType.serializer(),
                JsonElement.serializer()
            )
        )
}
