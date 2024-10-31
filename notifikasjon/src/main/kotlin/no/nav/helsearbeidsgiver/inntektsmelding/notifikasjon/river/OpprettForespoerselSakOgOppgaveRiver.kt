package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.NotifikasjonTekst
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.PaaminnelseToggle
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.opprettOppgave
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.opprettSak
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

data class OpprettForespoerselSakOgOppgaveMelding(
    val eventName: EventName,
    val transaksjonId: UUID,
    val forespoerselId: UUID,
    val orgnr: Orgnr,
    val sykmeldt: Person,
    val orgNavn: String,
    val skalHaPaaminnelse: Boolean,
    val forespoersel: Forespoersel?,
)

class OpprettForespoerselSakOgOppgaveRiver(
    private val lenkeBaseUrl: String,
    private val paaminnelseToggle: PaaminnelseToggle,
    private val agNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
) : ObjectRiver<OpprettForespoerselSakOgOppgaveMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): OpprettForespoerselSakOgOppgaveMelding? =
        if (setOf(Key.BEHOV, Key.FAIL).any(json::containsKey)) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()

            OpprettForespoerselSakOgOppgaveMelding(
                eventName = Key.EVENT_NAME.krev(EventName.SAK_OG_OPPGAVE_OPPRETT_REQUESTED, EventName.serializer(), json),
                transaksjonId = Key.UUID.les(UuidSerializer, json),
                forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, data),
                orgnr = Key.ORGNRUNDERENHET.les(Orgnr.serializer(), data),
                sykmeldt = Key.SYKMELDT.les(Person.serializer(), data),
                orgNavn = Key.VIRKSOMHET.les(String.serializer(), data),
                skalHaPaaminnelse = Key.SKAL_HA_PAAMINNELSE.les(Boolean.serializer(), data),
                forespoersel = Key.FORESPOERSEL.lesOrNull(Forespoersel.serializer(), data),
            )
        }

    override fun OpprettForespoerselSakOgOppgaveMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement> {
        val lenke = NotifikasjonTekst.lenkeAktivForespoersel(lenkeBaseUrl, forespoerselId)

        val sakId =
            agNotifikasjonKlient.opprettSak(
                lenke = lenke,
                inntektsmeldingTypeId = forespoerselId,
                orgnr = orgnr,
                sykmeldt = sykmeldt,
            )

        val oppgaveId =
            agNotifikasjonKlient.opprettOppgave(
                lenke = lenke,
                forespoerselId = forespoerselId,
                orgnr = orgnr,
                orgNavn = orgNavn,
                skalHaPaaminnelse = skalHaPaaminnelse,
                paaminnelseAktivert = paaminnelseToggle.oppgavePaaminnelseAktivert,
                tidMellomOppgaveopprettelseOgPaaminnelse = paaminnelseToggle.tidMellomOppgaveopprettelseOgPaaminnelse,
                sykmeldingsPerioder = forespoersel?.sykmeldingsperioder.orEmpty(),
            )

        return mapOf(
            Key.EVENT_NAME to EventName.SAK_OG_OPPGAVE_OPPRETTET.toJson(),
            Key.UUID to transaksjonId.toJson(),
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
                event = eventName,
                transaksjonId = transaksjonId,
                forespoerselId = forespoerselId,
                utloesendeMelding = json.toJson(),
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun OpprettForespoerselSakOgOppgaveMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@OpprettForespoerselSakOgOppgaveRiver),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId),
        )
}
