package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.filter

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.json.Json
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.PersisterImLøsning
import no.nav.helsearbeidsgiver.felles.json.customObjectMapper
import no.nav.helsearbeidsgiver.felles.json.toJsonNode

abstract class AbstractFilterBase {

    private val om = customObjectMapper()
    val BEHOV_NULL = mapOf(
        Key.EVENT_NAME.str to EventName.SAK_OPPRETTET
    )
    val BEHOV_UTEN = mapOf(
        Key.EVENT_NAME.str to EventName.INSENDING_STARTED,
        Key.BEHOV.str to "",
        Key.LØSNING.str to mapOf(
            BehovType.PERSISTER_IM to PersisterImLøsning("def")
        )
    )
    val BEHOV_ENKEL = mapOf(
        Key.EVENT_NAME.str to EventName.OPPGAVE_OPPRETTET,
        Key.BEHOV.str to BehovType.FULLT_NAVN,
        Key.LØSNING.str to mapOf(
            BehovType.FULLT_NAVN to PersisterImLøsning("ghi")
        )
    )
    val BEHOV_LISTE = mapOf(
        Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_JOURNALFOERT,
        Key.BEHOV.str to listOf(BehovType.TILGANGSKONTROLL, BehovType.ARBEIDSGIVERE),
        Key.LØSNING.str to mapOf(
            BehovType.ARBEIDSGIVERE to PersisterImLøsning("ghi"),
            BehovType.TILGANGSKONTROLL to PersisterImLøsning("ghi")
        )
    )
    private val BEHOV_UTEN_LØSNING = mapOf(
        Key.EVENT_NAME.str to EventName.INNTEKTSMELDING_JOURNALFOERT,
        Key.BEHOV.str to listOf(BehovType.TILGANGSKONTROLL, BehovType.ARBEIDSGIVERE)
    )
    private val BEHOV_ANNET = mapOf(
        Key.EVENT_NAME.str to EventName.OPPGAVE_OPPRETTET,
        Key.BEHOV.str to listOf(BehovType.ENDRE_SAK_STATUS, BehovType.ENDRE_OPPGAVE_STATUS)
    )
    val LISTE_UTEN_LØSNING = listOf(toNode(BEHOV_UTEN_LØSNING), toNode(BEHOV_ANNET))
    val LISTE_MED_LØSNING = listOf(toNode(BEHOV_LISTE))
    val EVENT = EventName.INNTEKTSMELDING_JOURNALFOERT

    fun toNode(msg: Map<String, Any>): JsonNode {
        return Json.parseToJsonElement(om.writeValueAsString(msg)).toJsonNode()
    }
}
