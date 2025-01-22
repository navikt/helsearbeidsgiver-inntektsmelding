package no.nav.helsearbeidsgiver.inntektsmelding.aareg

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Arbeidsforhold
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import java.util.UUID

data class HentArbeidsforholdMelding(
    val eventName: EventName,
    val behovType: BehovType,
    val kontekstId: UUID,
    val data: Map<Key, JsonElement>,
    val svarKafkaKey: KafkaKey,
    val fnr: Fnr,
)

class HentArbeidsforholdRiver(
    private val aaregClient: AaregClient,
) : ObjectRiver<HentArbeidsforholdMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): HentArbeidsforholdMelding? =
        if (Key.FAIL in json) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()

            HentArbeidsforholdMelding(
                eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
                behovType = Key.BEHOV.krev(BehovType.HENT_ARBEIDSFORHOLD, BehovType.serializer(), json),
                kontekstId = Key.KONTEKST_ID.les(UuidSerializer, json),
                data = data,
                svarKafkaKey = Key.SVAR_KAFKA_KEY.les(KafkaKey.serializer(), data),
                fnr = Key.FNR.les(Fnr.serializer(), data),
            )
        }

    override fun HentArbeidsforholdMelding.bestemNoekkel(): KafkaKey = svarKafkaKey

    override fun HentArbeidsforholdMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        val arbeidsforhold =
            Metrics.aaregRequest
                .recordTime(aaregClient::hentArbeidsforhold) {
                    aaregClient.hentArbeidsforhold(fnr.verdi, kontekstId.toString())
                }.map { it.tilArbeidsforhold() }

        "Fant ${arbeidsforhold.size} arbeidsforhold.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        // Logger dette midlertidig for å finne ut om det er mulig.
        // Det er det trolig ikke: https://www.skatteetaten.no/bedrift-og-organisasjon/arbeidsgiver/a-meldingen/veiledning/arbeidsforholdet/type-arbeidsforhold/ordinart-arbeidsforhold/#Hvilken-informasjon-skal-du-oppgi
        if (arbeidsforhold.any { it.ansettelsesperiode.periode.fom == null }) {
            sikkerLogger.info("Fant arbeidsforhold uten fra-dato. Dette logges kun for å vurdere forenklinger i koden.")
        }

        return mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                data
                    .plus(
                        Key.ARBEIDSFORHOLD to arbeidsforhold.toJson(Arbeidsforhold.serializer()),
                    ).toJson(),
        )
    }

    override fun HentArbeidsforholdMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke hente arbeidsforhold fra Aareg.",
                kontekstId = kontekstId,
                utloesendeMelding = json,
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun HentArbeidsforholdMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@HentArbeidsforholdRiver),
            Log.event(eventName),
            Log.behov(behovType),
            Log.kontekstId(kontekstId),
        )
}
