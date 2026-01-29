package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.domene.Person
import no.nav.hag.simba.utils.felles.json.krev
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.NotifikasjonTekst
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.opprettOppgave
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.opprettSak
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class OpprettForespoerselSakOgOppgaveMelding(
    val eventName: EventName,
    val kontekstId: UUID,
    val forespoerselId: UUID,
    val forespoersel: Forespoersel,
    val sykmeldt: Person,
    val orgNavn: String,
    val skalHaPaaminnelse: Boolean,
)

class OpprettForespoerselSakOgOppgaveRiver(
    private val lenkeBaseUrl: String,
    private val tidMellomOppgaveOpprettelseOgPaaminnelse: String,
    private val agNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
) : ObjectRiver.Simba<OpprettForespoerselSakOgOppgaveMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): OpprettForespoerselSakOgOppgaveMelding? =
        if (setOf(Key.BEHOV, Key.FAIL).any(json::containsKey)) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()

            OpprettForespoerselSakOgOppgaveMelding(
                eventName = Key.EVENT_NAME.krev(EventName.SAK_OG_OPPGAVE_OPPRETT_REQUESTED, EventName.serializer(), json),
                kontekstId = Key.KONTEKST_ID.les(UuidSerializer, json),
                forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, data),
                forespoersel = Key.FORESPOERSEL.les(Forespoersel.serializer(), data),
                sykmeldt = Key.SYKMELDT.les(Person.serializer(), data),
                orgNavn = Key.VIRKSOMHET.les(String.serializer(), data),
                skalHaPaaminnelse = Key.SKAL_HA_PAAMINNELSE.les(Boolean.serializer(), data),
            )
        }

    override fun OpprettForespoerselSakOgOppgaveMelding.bestemNoekkel(): KafkaKey = KafkaKey(forespoerselId)

    override fun OpprettForespoerselSakOgOppgaveMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        val lenke = NotifikasjonTekst.lenkeAktivForespoersel(lenkeBaseUrl, forespoerselId)

        val sakId =
            agNotifikasjonKlient.opprettSak(
                lenke = lenke,
                inntektsmeldingType = Inntektsmelding.Type.Forespurt(forespoerselId),
                orgnr = forespoersel.orgnr,
                sykmeldt = sykmeldt,
                sykmeldingsperioder = forespoersel.sykmeldingsperioder,
            )

        val oppgaveId =
            agNotifikasjonKlient.opprettOppgave(
                lenke = lenke,
                forespoerselId = forespoerselId,
                orgnr = forespoersel.orgnr,
                orgNavn = orgNavn,
                skalHaPaaminnelse = skalHaPaaminnelse,
                tidMellomOppgaveopprettelseOgPaaminnelse = tidMellomOppgaveOpprettelseOgPaaminnelse,
                sykmeldingsPerioder = forespoersel.sykmeldingsperioder,
            )

        return mapOf(
            Key.EVENT_NAME to EventName.SAK_OG_OPPGAVE_OPPRETTET.toJson(),
            Key.KONTEKST_ID to kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to forespoerselId.toJson(),
                    Key.SAK_ID to sakId.toJson(),
                    Key.OPPGAVE_ID to oppgaveId.toJson(),
                ).toJson(),
        )
    }

    override fun OpprettForespoerselSakOgOppgaveMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke opprette sak og/eller oppgave for forespurt inntektmelding.",
                kontekstId = kontekstId,
                utloesendeMelding = json,
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun OpprettForespoerselSakOgOppgaveMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@OpprettForespoerselSakOgOppgaveRiver),
            Log.event(eventName),
            Log.kontekstId(kontekstId),
            Log.forespoerselId(forespoerselId),
        )
}
