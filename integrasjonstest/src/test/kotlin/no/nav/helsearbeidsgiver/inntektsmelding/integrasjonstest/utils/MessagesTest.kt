package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils

import io.kotest.core.spec.style.FunSpec
import io.kotest.data.row
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
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
import no.nav.helsearbeidsgiver.felles.VirksomhetLøsning
import no.nav.helsearbeidsgiver.felles.test.json.fromJsonMapOnlyKeys
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.fromJsonMapFiltered
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.juli

class MessagesTest : FunSpec({

    test("finner korrekt melding for event") {
        val expectedEventName = EventName.TRENGER_REQUESTED

        val funnetMelding = Mock.meldingerMedBehovMedLoesning.filter(expectedEventName).first()

        val actualEventName = funnetMelding.fromJsonMapOnlyKeys()[Key.EVENT_NAME]?.fromJson(EventName.serializer())

        actualEventName shouldBe expectedEventName
    }

    test("finner ikke manglende melding for event") {
        Mock.meldingerMedBehovMedLoesning.filter(EventName.FORESPØRSEL_MOTTATT)
            .all()
            .shouldBeEmpty()
    }

    context("finner korrekt melding for behov uten løsning") {
        withData(
            BehovType.FULLT_NAVN,
            BehovType.VIRKSOMHET
        ) { expectedBehovType ->
            val funnetMelding = Mock.meldingerMedBehovMedLoesning.filter(expectedBehovType, loesningPaakrevd = false).first()

            funnetMelding.fromJsonMapOnlyKeys().let {
                val behovJson = it[Key.BEHOV].shouldNotBeNull()

                behovJson.fromJson(BehovType.serializer().list()) shouldContain expectedBehovType
            }
        }
    }

    test("finner ikke manglende melding for behov uten løsning") {
        Mock.meldingerMedBehovMedLoesning.filter(BehovType.HENT_IM_ORGNR, loesningPaakrevd = false)
            .all()
            .shouldBeEmpty()
    }

    context("finner korrekt melding for behov med løsning") {
        withData(
            nameFn = { (behovType, _, _) -> behovType.name },
            row(BehovType.FULLT_NAVN, Mock.personDato, NavnLøsning.serializer()),
            row(BehovType.VIRKSOMHET, Mock.ORGNR, VirksomhetLøsning.serializer())
        ) { (expectedBehovType, expectedLoesning, loesningSerializer) ->
            val funnetMelding = Mock.meldingerMedBehovMedLoesning.filter(expectedBehovType, loesningPaakrevd = true).first()

            funnetMelding.fromJsonMapOnlyKeys().let {
                val behovJson = it[Key.BEHOV].shouldNotBeNull()

                behovJson.fromJson(BehovType.serializer().list()) shouldContain expectedBehovType

                val loesning = it.lesLoesning(expectedBehovType, loesningSerializer)

                loesning?.error.shouldBeNull()
                loesning?.value shouldBe expectedLoesning
            }
        }
    }

    test("finner ikke manglende melding for behov med løsning") {
        Mock.meldingerMedBehovUtenLoesning.filter(BehovType.VIRKSOMHET, loesningPaakrevd = true)
            .all()
            .shouldBeEmpty()
    }

    test("finner korrekt melding for datafelt") {
        val funnetMelding = Mock.meldingerMedDatafelt.filter(DataFelt.VIRKSOMHET).first()

        funnetMelding.fromJsonMapOnlyKeys() shouldContainKey Key.DATA

        funnetMelding.fromJsonMapFiltered(DataFelt.serializer()).let {
            it[DataFelt.VIRKSOMHET]?.fromJson(String.serializer()) shouldBe Mock.ORGNR
        }
    }

    test("finner ikke manglende melding for datafelt") {
        Mock.meldingerMedDatafelt.filter(DataFelt.ARBEIDSFORHOLD)
            .all()
            .shouldBeEmpty()
    }
})

private object Mock {
    const val ORGNR = "orgnr-pai"
    val personDato = PersonDato(
        navn = "Thomas Toget",
        fødselsdato = 11.juli
    )

    val meldingerMedBehovUtenLoesning = basisfelt().toJson().toMessages()
    val meldingerMedBehovMedLoesning = basisfelt().plus(loesninger()).toJson().toMessages()
    val meldingerMedDatafelt = basisfelt().plus(datafelt()).toJson().toMessages()

    private fun basisfelt(): Map<String, JsonElement> =
        mapOf(
            Key.EVENT_NAME.str to EventName.TRENGER_REQUESTED.toJson(EventName.serializer()),
            Key.BEHOV.str to listOf(
                BehovType.FULLT_NAVN,
                BehovType.VIRKSOMHET,
                BehovType.ARBEIDSFORHOLD
            ).toJson(BehovType.serializer()),
            Key.DATA.str to "".toJson()
        )

    private fun loesninger(): Pair<String, JsonElement> =
        Pair(
            Key.LØSNING.str,
            mapOf(
                BehovType.FULLT_NAVN to NavnLøsning(personDato).toJson(NavnLøsning.serializer()),
                BehovType.VIRKSOMHET to VirksomhetLøsning(ORGNR).toJson(VirksomhetLøsning.serializer())
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
