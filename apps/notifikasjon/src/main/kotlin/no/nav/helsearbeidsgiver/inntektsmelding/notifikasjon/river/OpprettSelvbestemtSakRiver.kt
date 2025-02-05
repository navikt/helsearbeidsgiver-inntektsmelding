package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifkasjon.graphql.generated.enums.SaksStatus
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.NotifikasjonTekst
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.opprettSak
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class OpprettSelvbestemtSakMelding(
    val eventName: EventName,
    val behovType: BehovType,
    val kontekstId: UUID,
    val data: Map<Key, JsonElement>,
    val inntektsmelding: Inntektsmelding,
)

class OpprettSelvbestemtSakRiver(
    private val linkUrl: String,
    private val agNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
) : ObjectRiver<OpprettSelvbestemtSakMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): OpprettSelvbestemtSakMelding? =
        if (Key.FAIL in json) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()

            OpprettSelvbestemtSakMelding(
                eventName = Key.EVENT_NAME.les(EventName.serializer(), json),
                behovType = Key.BEHOV.krev(BehovType.OPPRETT_SELVBESTEMT_SAK, BehovType.serializer(), json),
                kontekstId = Key.KONTEKST_ID.les(UuidSerializer, json),
                data = data,
                inntektsmelding = Key.SELVBESTEMT_INNTEKTSMELDING.les(Inntektsmelding.serializer(), data),
            )
        }

    override fun OpprettSelvbestemtSakMelding.bestemNoekkel(): KafkaKey = KafkaKey(inntektsmelding.type.id)

    override fun OpprettSelvbestemtSakMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        val sakId =
            agNotifikasjonKlient.opprettSak(
                lenke = NotifikasjonTekst.lenkeFerdigstiltSelvbestemt(linkUrl, inntektsmelding.type.id),
                inntektsmeldingTypeId = inntektsmelding.type.id,
                orgnr = inntektsmelding.avsender.orgnr,
                sykmeldt = inntektsmelding.sykmeldt.let { Person(it.fnr, it.navn) },
                sykmeldingsperioder = inntektsmelding.sykmeldingsperioder,
                initiellStatus = SaksStatus.FERDIG,
            )

        return mapOf(
            Key.EVENT_NAME to eventName.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                data
                    .plus(
                        Key.SAK_ID to sakId.toJson(),
                    ).toJson(),
        )
    }

    override fun OpprettSelvbestemtSakMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke lagre sak for selvbestemt inntektsmelding.",
                kontekstId = kontekstId,
                utloesendeMelding = json,
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun OpprettSelvbestemtSakMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@OpprettSelvbestemtSakRiver),
            Log.event(eventName),
            Log.behov(behovType),
            Log.kontekstId(kontekstId),
            Log.selvbestemtId(inntektsmelding.type.id),
        )
}
