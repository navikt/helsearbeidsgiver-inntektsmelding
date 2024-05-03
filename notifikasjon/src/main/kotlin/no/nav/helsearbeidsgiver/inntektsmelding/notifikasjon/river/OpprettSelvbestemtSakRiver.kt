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
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.db.SelvbestemtRepo
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.opprettSak
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class OpprettSelvbestemtSakMelding(
    val eventName: EventName,
    val behovType: BehovType,
    val transaksjonId: UUID,
    val inntektsmelding: Inntektsmelding
)

// TODO test
class OpprettSelvbestemtSakRiver(
    private val linkUrl: String,
    private val selvbestemtRepo: SelvbestemtRepo,
    private val agNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient
) : ObjectRiver<OpprettSelvbestemtSakMelding>() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): OpprettSelvbestemtSakMelding? =
        if (setOf(Key.BEHOV, Key.DATA, Key.FAIL).any(json::containsKey)) {
            null
        } else {
            OpprettSelvbestemtSakMelding(
                eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
                behovType = Key.BEHOV.krev(BehovType.OPPRETT_SELVBESTEMT_SAK, BehovType.serializer(), json),
                transaksjonId = Key.UUID.les(UuidSerializer, json),
                inntektsmelding = Key.SELVBESTEMT_INNTEKTSMELDING.les(Inntektsmelding.serializer(), json)
            )
        }

    override fun OpprettSelvbestemtSakMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        val sakId = agNotifikasjonKlient.opprettSak(
            lenke = "$linkUrl/im-dialog/kvittering/agi/${inntektsmelding.type.id}",
            inntektsmeldingTypeId = inntektsmelding.type.id,
            orgnr = inntektsmelding.avsender.orgnr,
            sykmeldtNavn = inntektsmelding.sykmeldt.navn,
            sykmeldtFoedselsdato = inntektsmelding.sykmeldt.fnr.take(6),
            initiellStatus = SaksStatus.FERDIG
        )

        return MdcUtils.withLogFields(
            Log.sakId(sakId)
        ) {
            selvbestemtRepo.lagreSakId(inntektsmelding.type.id, sakId)

            mapOf(
                Key.EVENT_NAME to eventName.toJson(),
                Key.UUID to transaksjonId.toJson(),
                Key.DATA to "".toJson(),
                Key.SAK_ID to sakId.toJson()
            )
        }
    }

    override fun OpprettSelvbestemtSakMelding.haandterFeil(json: Map<Key, JsonElement>, error: Throwable): Map<Key, JsonElement> {
        val fail = Fail(
            feilmelding = "Klarte ikke lagre sak for selvbestemt inntektsmelding.",
            event = eventName,
            transaksjonId = transaksjonId,
            forespoerselId = null,
            utloesendeMelding = json.toJson()
        )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
            .minus(Key.FORESPOERSEL_ID)
            .plus(Key.SELVBESTEMT_ID to inntektsmelding.type.id.toJson())
    }

    override fun OpprettSelvbestemtSakMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@OpprettSelvbestemtSakRiver),
            Log.event(eventName),
            Log.behov(behovType),
            Log.transaksjonId(transaksjonId),
            Log.selvbestemtId(inntektsmelding.type.id)
        )
}
