package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.felles.utils.erForespurt
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.NotifikasjonTekst
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.ferdigstillOppgave
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.ferdigstillSak
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class FerdigstillMelding(
    val eventName: EventName,
    val kontekstId: UUID,
    val imType: Inntektsmelding.Type,
)

class FerdigstillSakOgOppgaveRiver(
    private val linkUrl: String,
    private val agNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
) : ObjectRiver.Simba<FerdigstillMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): FerdigstillMelding? =
        if (setOf(Key.BEHOV, Key.FAIL).any(json::containsKey)) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()
            val eventName = Key.EVENT_NAME.les(EventName.serializer(), json)

            // Støtter både inntektmelding mottatt av Simba og event fra Storebror
            val imType =
                when (eventName) {
                    EventName.INNTEKTSMELDING_MOTTATT -> {
                        Key.FORESPOERSEL_ID
                            .les(UuidSerializer, data)
                            .let(Inntektsmelding.Type::Forespurt)
                    }

                    EventName.SELVBESTEMT_IM_LAGRET -> {
                        val im = Key.SELVBESTEMT_INNTEKTSMELDING.les(Inntektsmelding.serializer(), data)
                        when (im.aarsakInnsending) {
                            // Nye selvbestemte inntektsmeldinger oppretter en ny, allerede ferdigstilt sak i OpprettSelvbestemtSakRiver
                            AarsakInnsending.Ny -> null

                            AarsakInnsending.Endring -> im.type
                        }
                    }

                    EventName.FORESPOERSEL_BESVART -> {
                        Key.FORESPOERSEL_ID
                            .les(UuidSerializer, data)
                            .let(Inntektsmelding.Type::Forespurt)
                    }

                    else -> {
                        null
                    }
                }

            if (imType == null) {
                null
            } else {
                FerdigstillMelding(
                    eventName = eventName,
                    kontekstId = Key.KONTEKST_ID.les(UuidSerializer, json),
                    imType = imType,
                )
            }
        }

    override fun FerdigstillMelding.bestemNoekkel(): KafkaKey = KafkaKey(imType.id)

    override fun FerdigstillMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        val erForespurt = imType.erForespurt()

        if (erForespurt) {
            val lenke = NotifikasjonTekst.lenkeFerdigstiltForespoersel(linkUrl, imType.id)

            agNotifikasjonKlient.ferdigstillSak(lenke, imType.id)
            agNotifikasjonKlient.ferdigstillOppgave(lenke, imType.id)
        } else {
            agNotifikasjonKlient.ferdigstillSak(null, imType.id)
        }

        val idKey =
            if (erForespurt) {
                Key.FORESPOERSEL_ID
            } else {
                Key.SELVBESTEMT_ID
            }

        return mapOf(
            Key.EVENT_NAME to EventName.SAK_OG_OPPGAVE_FERDIGSTILT.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            idKey to imType.id.toJson(),
        )
    }

    override fun FerdigstillMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke ferdigstille sak og/eller oppgave for inntektmelding.",
                kontekstId = kontekstId,
                utloesendeMelding = json,
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun FerdigstillMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@FerdigstillSakOgOppgaveRiver),
            Log.event(eventName),
            Log.kontekstId(kontekstId),
            Log.inntektsmeldingTypeId(imType),
        )
}
