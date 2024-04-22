package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Orgnr
import no.nav.helsearbeidsgiver.felles.Person
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.loeser.ObjectRiver
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.db.ForespoerselSakRepo
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.opprettSak
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class OpprettForespoerselSakMelding(
    val eventName: EventName,
    val transaksjonId: UUID,
    val forespoerselId: UUID,
    val orgnr: Orgnr,
    val person: Person
)

// TODO test
class OpprettForespoerselSakRiver(
    private val lenkeBaseUrl: String,
    private val agNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    private val forespoerselSakRepo: ForespoerselSakRepo
) : ObjectRiver<OpprettForespoerselSakMelding>() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): OpprettForespoerselSakMelding? =
        if (setOf(Key.BEHOV, Key.DATA, Key.FAIL).any(json::containsKey)) {
            null
        } else {
            OpprettForespoerselSakMelding(
                eventName = Key.EVENT_NAME.krev(EventName.SAK_OPPRETT_REQUESTED, EventName.serializer(), json),
                transaksjonId = Key.UUID.les(UuidSerializer, json),
                forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, json),
                orgnr = Key.ORGNRUNDERENHET.les(Orgnr.serializer(), json),
                // TODO hvilken key??
                person = Key.PERSONER.les(Person.serializer(), json)
            )
        }

    override fun OpprettForespoerselSakMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        val sakId = agNotifikasjonKlient.opprettSak(
            linkUrl = lenkeBaseUrl,
            inntektsmeldingId = forespoerselId,
            orgnr = orgnr.verdi,
            sykmeldtNavn = person.navn,
            sykmeldtFoedselsdato = person.fnr.take(6)
        )

        return MdcUtils.withLogFields(
            Log.sakId(sakId)
        ) {
            forespoerselSakRepo.lagreSakId(forespoerselId, sakId)

            mapOf(
                Key.EVENT_NAME to EventName.SAK_OPPRETTET.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                Key.SAK_ID to sakId.toJson()
            )
        }
    }

    override fun OpprettForespoerselSakMelding.haandterFeil(json: Map<Key, JsonElement>, error: Throwable): Map<Key, JsonElement> {
        val fail = Fail(
            feilmelding = "Klarte ikke opprette/lagre sak for forespurt inntektmelding.",
            event = eventName,
            transaksjonId = transaksjonId,
            forespoerselId = forespoerselId,
            utloesendeMelding = json.toJson()
        )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun OpprettForespoerselSakMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@OpprettForespoerselSakRiver),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId)
        )
}
