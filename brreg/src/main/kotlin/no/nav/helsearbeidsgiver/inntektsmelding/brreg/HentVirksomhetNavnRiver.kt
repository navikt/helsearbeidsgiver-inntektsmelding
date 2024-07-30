package no.nav.helsearbeidsgiver.inntektsmelding.brreg

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.brreg.Virksomhet
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.loeser.ObjectRiver
import no.nav.helsearbeidsgiver.felles.metrics.Metrics
import no.nav.helsearbeidsgiver.felles.metrics.recordTime
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.set
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

data class HentVirksomhetMelding(
    val eventName: EventName,
    val behovType: BehovType,
    val transaksjonId: UUID,
    val data: Map<Key, JsonElement>,
    val orgnr: Set<Orgnr>,
)

class HentVirksomhetNavnRiver(
    private val brregClient: BrregClient,
    private val isPreProd: Boolean,
) : ObjectRiver<HentVirksomhetMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): HentVirksomhetMelding? =
        if (Key.FAIL in json) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()

            HentVirksomhetMelding(
                eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
                behovType = Key.BEHOV.krev(BehovType.HENT_VIRKSOMHET_NAVN, BehovType.serializer(), json),
                transaksjonId = Key.UUID.les(UuidSerializer, json),
                data = data,
                orgnr = Key.ORGNR_UNDERENHETER.les(Orgnr.serializer().set(), data),
            )
        }

    override fun HentVirksomhetMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        val orgnrMedNavn =
            if (isPreProd) {
                brukPreprodOrg(orgnr)
            } else {
                Metrics.brregRequest.recordTime(brregClient::hentVirksomheter) {
                    brregClient.hentVirksomheter(orgnr.map { it.verdi })
                }
            }.associate { it.organisasjonsnummer to it.navn }

        return mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.DATA to
                data
                    .plus(Key.VIRKSOMHETER to orgnrMedNavn.toJson())
                    .toJson(),
        )
    }

    override fun HentVirksomhetMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke hente virksomhet fra Brreg.",
                event = eventName,
                transaksjonId = transaksjonId,
                forespoerselId = null,
                utloesendeMelding = json.toJson(),
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun HentVirksomhetMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@HentVirksomhetNavnRiver),
            Log.event(eventName),
            Log.behov(behovType),
            Log.transaksjonId(transaksjonId),
        )
}

private fun brukPreprodOrg(orgnr: Set<Orgnr>): List<Virksomhet> {
    val preprodOrgnr =
        mapOf(
            "810007702" to "ANSTENDIG PIGGSVIN BYDEL",
            "810007842" to "ANSTENDIG PIGGSVIN BARNEHAGE",
            "810008032" to "ANSTENDIG PIGGSVIN BRANNVESEN",
            "810007982" to "ANSTENDIG PIGGSVIN SYKEHJEM",
        )

    return orgnr.map {
        Virksomhet(
            navn = preprodOrgnr.getOrDefault(it.verdi, "Ukjent arbeidsgiver"),
            organisasjonsnummer = it.verdi,
        )
    }
}
