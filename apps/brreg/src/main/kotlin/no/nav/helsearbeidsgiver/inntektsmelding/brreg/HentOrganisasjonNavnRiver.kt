package no.nav.helsearbeidsgiver.inntektsmelding.brreg

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.json.krev
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.orgMapSerializer
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.model.Fail
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

data class HentOrganisasjonMelding(
    val eventName: EventName,
    val behovType: BehovType,
    val kontekstId: UUID,
    val data: Map<Key, JsonElement>,
    val svarKafkaKey: KafkaKey,
    val orgnr: Set<Orgnr>,
)

class HentOrganisasjonNavnRiver(
    private val brregClient: BrregClient,
    private val isPreProd: Boolean,
) : ObjectRiver.Simba<HentOrganisasjonMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): HentOrganisasjonMelding? =
        if (Key.FAIL in json) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()

            HentOrganisasjonMelding(
                eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
                behovType = Key.BEHOV.krev(BehovType.HENT_VIRKSOMHET_NAVN, BehovType.serializer(), json),
                kontekstId = Key.KONTEKST_ID.les(UuidSerializer, json),
                data = data,
                svarKafkaKey = Key.SVAR_KAFKA_KEY.les(KafkaKey.serializer(), data),
                orgnr = Key.ORGNR_UNDERENHETER.les(Orgnr.serializer().set(), data),
            )
        }

    override fun HentOrganisasjonMelding.bestemNoekkel(): KafkaKey = svarKafkaKey

    override fun HentOrganisasjonMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        val orgnrMedNavn =
            if (isPreProd) {
                brukPreprodOrg(orgnr)
            } else {
                runBlocking {
                    brregClient.hentOrganisasjonNavn(orgnr)
                }
            }

        if (orgnr.size != orgnrMedNavn.size) {
            "Ba om ${orgnr.size} organisasjonsnavn fra Brreg og mottok ${orgnrMedNavn.size}.".also {
                logger.info(it)
                sikkerLogger.info(it)
            }
        }

        return mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                data
                    .plus(Key.VIRKSOMHETER to orgnrMedNavn.toJson(orgMapSerializer))
                    .toJson(),
        )
    }

    override fun HentOrganisasjonMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke hente organisasjon fra Brreg.",
                kontekstId = kontekstId,
                utloesendeMelding = json,
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun HentOrganisasjonMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@HentOrganisasjonNavnRiver),
            Log.event(eventName),
            Log.behov(behovType),
            Log.kontekstId(kontekstId),
        )
}

private fun brukPreprodOrg(orgnr: Set<Orgnr>): Map<Orgnr, String> =
    orgnr.associateWith {
        when (it.verdi) {
            "810007702" -> "ANSTENDIG PIGGSVIN BYDEL"
            "810007842" -> "ANSTENDIG PIGGSVIN BARNEHAGE"
            "810007982" -> "ANSTENDIG PIGGSVIN SYKEHJEM"
            "810008032" -> "ANSTENDIG PIGGSVIN BRANNVESEN"
            else -> "UKJENT ARBEIDSGIVER"
        }
    }
