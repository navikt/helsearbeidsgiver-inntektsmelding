package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.json.krev
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.toMap
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.river.ObjectRiver
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.endreOppgavePaaminnelse
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

data class EndreOppgavePaaminnelseMelding(
    val eventName: EventName,
    val kontekstId: UUID,
    val forespoerselId: UUID,
    val forespoersel: Forespoersel,
    val orgNavn: String,
)

class EndreOppgavePaaminnelseRiver(
    private val tidMellomOppgaveOpprettelseOgPaaminnelse: String,
    private val agNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
) : ObjectRiver.Simba<EndreOppgavePaaminnelseMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): EndreOppgavePaaminnelseMelding? =
        if (setOf(Key.BEHOV, Key.FAIL).any(json::containsKey)) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()

            EndreOppgavePaaminnelseMelding(
                eventName = Key.EVENT_NAME.krev(EventName.ENDRE_OPPGAVE_PAAMINNELSE_REQUESTED, EventName.serializer(), json),
                kontekstId = Key.KONTEKST_ID.les(UuidSerializer, json),
                forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, data),
                forespoersel = Key.FORESPOERSEL.les(Forespoersel.serializer(), data),
                orgNavn = Key.VIRKSOMHET.les(String.serializer(), data),
            )
        }

    override fun EndreOppgavePaaminnelseMelding.bestemNoekkel(): KafkaKey = KafkaKey(forespoerselId)

    override fun EndreOppgavePaaminnelseMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement>? {
        agNotifikasjonKlient.endreOppgavePaaminnelse(
            forespoerselId = forespoerselId,
            orgnr = forespoersel.orgnr,
            orgNavn = orgNavn,
            tidMellomOppgaveopprettelseOgPaaminnelse = tidMellomOppgaveOpprettelseOgPaaminnelse,
            sykmeldingsPerioder = forespoersel.sykmeldingsperioder,
        )

        return null
    }

    override fun EndreOppgavePaaminnelseMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke endre påminnelse på oppgave.",
                kontekstId = kontekstId,
                utloesendeMelding = json,
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun EndreOppgavePaaminnelseMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@EndreOppgavePaaminnelseRiver),
            Log.event(eventName),
            Log.kontekstId(kontekstId),
            Log.forespoerselId(forespoerselId),
        )
}
