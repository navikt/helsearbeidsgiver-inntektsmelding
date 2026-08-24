package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.orgMapSerializer
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.Publisher
import no.nav.hag.simba.utils.rr.service.ServiceMed2Steg
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

data class EndreOppgaveSteg0(
    val kontekstId: UUID,
    val forespoerselId: UUID,
)

data class EndreOppgaveSteg1(
    val forespoersel: Forespoersel,
)

data class EndreOppgaveSteg2(
    val orgnrMedNavn: Map<Orgnr, String>,
)

class HentDataTilEndreOppgaveService(
    private val publisher: Publisher,
) : ServiceMed2Steg<EndreOppgaveSteg0, EndreOppgaveSteg1, EndreOppgaveSteg2>() {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val initialEventName = EventName.OVERSTYR_OPPGAVE_PAAMINNELSE_REQUESTED
    override val serviceEventName = EventName.SERVICE_HENT_DATA_TIL_ENDRE_OPPGAVE

    override fun lesSteg0(melding: Map<Key, JsonElement>): EndreOppgaveSteg0 =
        EndreOppgaveSteg0(
            kontekstId = Key.KONTEKST_ID.les(UuidSerializer, melding),
            forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): EndreOppgaveSteg1 =
        EndreOppgaveSteg1(
            forespoersel = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding),
        )

    override fun lesSteg2(melding: Map<Key, JsonElement>): EndreOppgaveSteg2 =
        EndreOppgaveSteg2(
            orgnrMedNavn = Key.VIRKSOMHETER.les(orgMapSerializer, melding),
        )

    override fun utfoerSteg0(
        data: Map<Key, JsonElement>,
        steg0: EndreOppgaveSteg0,
    ) {
        "Henter forespørsel for å endre oppgave-påminnelse.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        publisher.publish(
            key = steg0.forespoerselId,
            Key.EVENT_NAME to serviceEventName.toJson(),
            Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
            Key.KONTEKST_ID to steg0.kontekstId.toJson(),
            Key.DATA to
                data
                    .plus(
                        Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                    ).toJson(),
        )
    }

    override fun utfoerSteg1(
        data: Map<Key, JsonElement>,
        steg0: EndreOppgaveSteg0,
        steg1: EndreOppgaveSteg1,
    ) {
        "Forespørsel hentet. Henter virksomhetsnavn.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        publisher.publish(
            key = steg0.forespoerselId,
            Key.EVENT_NAME to serviceEventName.toJson(),
            Key.BEHOV to BehovType.HENT_VIRKSOMHET_NAVN.toJson(),
            Key.KONTEKST_ID to steg0.kontekstId.toJson(),
            Key.DATA to
                data
                    .plus(
                        mapOf(
                            Key.SVAR_KAFKA_KEY to KafkaKey(steg0.forespoerselId).toJson(),
                            Key.ORGNR_UNDERENHETER to setOf(steg1.forespoersel.orgnr).toJson(Orgnr.serializer()),
                        ),
                    ).toJson(),
        )
    }

    override fun utfoerSteg2(
        data: Map<Key, JsonElement>,
        steg0: EndreOppgaveSteg0,
        steg1: EndreOppgaveSteg1,
        steg2: EndreOppgaveSteg2,
    ) {
        "Data hentet. Sender event for å endre oppgave-påminnelse.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        val orgNavn = steg2.orgnrMedNavn[steg1.forespoersel.orgnr] ?: ORG_NAVN_DEFAULT

        publisher.publish(
            key = steg0.forespoerselId,
            Key.EVENT_NAME to EventName.ENDRE_OPPGAVE_PAAMINNELSE_REQUESTED.toJson(),
            Key.KONTEKST_ID to steg0.kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                    Key.FORESPOERSEL to steg1.forespoersel.toJson(),
                    Key.VIRKSOMHET to orgNavn.toJson(),
                ).toJson(),
        )
    }

    override fun EndreOppgaveSteg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@HentDataTilEndreOppgaveService),
            Log.event(serviceEventName),
            Log.kontekstId(kontekstId),
            Log.forespoerselId(forespoerselId),
        )

    override fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    ) {
        "Klarte ikke hente data for å endre oppgave-påminnelse pga. feil: '${fail.feilmelding}'".also {
            logger.error(it)
            sikkerLogger.error(it)
        }
    }
}
