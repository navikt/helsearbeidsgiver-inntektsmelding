package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.river

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.OppgaveAlleredeUtfoertException
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.Paaminnelse
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.SakEllerOppgaveFinnesIkkeException
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Periode
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.json.krev
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.river.ObjectRiver
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.NotifikasjonTekst
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

data class EndrePaaminnelseMelding(
    val eventName: EventName,
    val transaksjonId: UUID,
    val forespoerselId: UUID,
    val forespoersel: Forespoersel,
    val orgNavn: String,
)

// River for å opprette påminnelser på eksisterende oppgaver
class EndrePaaminnelseRiver(
    val agNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
) : ObjectRiver<EndrePaaminnelseMelding>() {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    override fun les(json: Map<Key, JsonElement>): EndrePaaminnelseMelding? =
        if (Key.FAIL in json) {
            null
        } else {
            val data = json[Key.DATA]?.toMap().orEmpty()

            EndrePaaminnelseMelding(
                eventName = Key.EVENT_NAME.krev(EventName.OPPGAVE_ENDRE_PAAMINNELSE_REQUESTED, EventName.serializer(), json),
                transaksjonId = Key.KONTEKST_ID.les(UuidSerializer, json),
                forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, data),
                forespoersel = Key.FORESPOERSEL.les(Forespoersel.serializer(), data),
                orgNavn = Key.VIRKSOMHET.les(String.serializer(), data),
            )
        }

    override fun EndrePaaminnelseMelding.haandter(json: Map<Key, JsonElement>): Map<Key, JsonElement>? {
        endreOppgavePaaminnelser(
            forespoerselId = forespoerselId,
            orgnr = forespoersel.orgnr.let(::Orgnr),
            orgNavn = orgNavn,
            sykmeldingsperioder = forespoersel.sykmeldingsperioder,
        )

        return null
    }

    override fun EndrePaaminnelseMelding.haandterFeil(
        json: Map<Key, JsonElement>,
        error: Throwable,
    ): Map<Key, JsonElement> {
        val fail =
            Fail(
                feilmelding = "Klarte ikke endre påminnelse på oppgave.",
                event = eventName,
                transaksjonId = transaksjonId,
                forespoerselId = forespoerselId,
                utloesendeMelding = json.toJson(),
            )

        logger.error(fail.feilmelding)
        sikkerLogger.error(fail.feilmelding, error)

        return fail.tilMelding()
    }

    override fun EndrePaaminnelseMelding.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@EndrePaaminnelseRiver),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId),
        )

    private fun endreOppgavePaaminnelser(
        forespoerselId: UUID,
        orgnr: Orgnr,
        orgNavn: String,
        sykmeldingsperioder: List<Periode>,
    ) {
        runCatching {
            runBlocking {
                agNotifikasjonKlient.endreOppgavePaaminnelserByEksternId(
                    merkelapp = NotifikasjonTekst.MERKELAPP,
                    eksternId = forespoerselId.toString(),
                    paaminnelse =
                        Paaminnelse(
                            tittel = NotifikasjonTekst.PAAMINNELSE_TITTEL,
                            innhold =
                                NotifikasjonTekst.paaminnelseInnhold(
                                    orgnr = orgnr,
                                    orgNavn = orgNavn,
                                    sykmeldingsperioder = sykmeldingsperioder,
                                ),
                            tidMellomOppgaveopprettelseOgPaaminnelse = "P21D",
                        ),
                )
            }
        }.onFailure {
            when (it) {
                is SakEllerOppgaveFinnesIkkeException, is OppgaveAlleredeUtfoertException -> {
                    logger.warn(it.message)
                    sikkerLogger.warn(it.message)
                }

                else -> throw it
            }
        }
    }
}
