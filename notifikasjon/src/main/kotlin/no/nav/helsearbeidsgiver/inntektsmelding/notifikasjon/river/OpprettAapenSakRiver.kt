package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifkasjon.graphql.generated.enums.SaksStatus
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.loeser.ObjectRiver
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.db.AapenRepo
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.opprettSak
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class OpprettAapenSakMelding(
    val eventName: EventName,
    val behovType: BehovType,
    val transaksjonId: UUID,
    val inntektsmelding: Inntektsmelding
)

// TODO test
class OpprettAapenSakRiver(
    private val linkUrl: String,
    private val aapenRepo: AapenRepo,
    private val agNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient
) : ObjectRiver<OpprettAapenSakMelding>() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): OpprettAapenSakMelding? =
        if (setOf(Key.BEHOV, Key.DATA, Key.FAIL).any(json::containsKey)) {
            null
        } else {
            OpprettAapenSakMelding(
                eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
                behovType = Key.BEHOV.krev(BehovType.OPPRETT_AAPEN_SAK, BehovType.serializer(), json),
                transaksjonId = Key.UUID.les(UuidSerializer, json),
                inntektsmelding = Key.AAPEN_INNTEKTMELDING.les(Inntektsmelding.serializer(), json)
            )
        }

    override fun OpprettAapenSakMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        val sakId = agNotifikasjonKlient.opprettSak(
            linkUrl = linkUrl,
            inntektsmeldingId = inntektsmelding.id,
            orgnr = inntektsmelding.avsender.orgnr,
            sykmeldtNavn = inntektsmelding.sykmeldt.navn,
            sykmeldtFoedselsdato = inntektsmelding.sykmeldt.fnr.take(6),
            initiellStatus = SaksStatus.FERDIG
        )

        return MdcUtils.withLogFields(
            Log.sakId(sakId)
        ) {
            aapenRepo.lagreSakId(inntektsmelding.id, sakId)

            mapOf(
                Key.EVENT_NAME to eventName.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.DATA to "".toJson(),
                Key.SAK_ID to sakId.toJson()
            )
        }
    }

    override fun OpprettAapenSakMelding.haandterFeil(json: Map<Key, JsonElement>, error: Throwable): Map<Key, JsonElement> {
        val fail = Fail(
            feilmelding = "Klarte ikke lagre sak for åpen inntektsmelding.",
            event = eventName,
            transaksjonId = transaksjonId,
            forespoerselId = null,
            utloesendeMelding = json.toJson()
        )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
            .minus(Key.FORESPOERSEL_ID)
            .plus(Key.AAPEN_ID to inntektsmelding.id.toJson())
    }

    override fun OpprettAapenSakMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this),
            Log.event(eventName),
            Log.behov(behovType),
            Log.transaksjonId(transaksjonId),
            Log.aapenId(inntektsmelding.id)
        )
}
