package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Fail
import no.nav.hag.simba.utils.felles.domene.Person
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.orgMapSerializer
import no.nav.hag.simba.utils.felles.json.personMapSerializer
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.Publisher
import no.nav.hag.simba.utils.rr.service.ServiceMed2Steg
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

data class Steg0(
    val kontekstId: UUID,
    val forespoerselId: UUID,
    val forespoersel: Forespoersel,
    val skalHaPaaminnelse: Boolean,
)

data class Steg1(
    val orgnrMedNavn: Map<Orgnr, String>,
)

data class Steg2(
    val personer: Map<Fnr, Person>,
)

class HentDataTilSakOgOppgaveService(
    private val publisher: Publisher,
) : ServiceMed2Steg<Steg0, Steg1, Steg2>() {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.FORESPOERSEL_MOTTATT

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            kontekstId = Key.KONTEKST_ID.les(UuidSerializer, melding),
            forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding),
            forespoersel = Key.FORESPOERSEL.les(Forespoersel.serializer(), melding),
            skalHaPaaminnelse = Key.SKAL_HA_PAAMINNELSE.les(Boolean.serializer(), melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            orgnrMedNavn = Key.VIRKSOMHETER.les(orgMapSerializer, melding),
        )

    override fun lesSteg2(melding: Map<Key, JsonElement>): Steg2 =
        Steg2(
            personer = Key.PERSONER.les(personMapSerializer, melding),
        )

    override fun utfoerSteg0(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
    ) {
        "Mottok event $eventName. Henter data for å opprette sak og oppgave.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        publisher.publish(
            key = steg0.forespoerselId,
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to BehovType.HENT_VIRKSOMHET_NAVN.toJson(),
            Key.KONTEKST_ID to steg0.kontekstId.toJson(),
            Key.DATA to
                data
                    .plus(
                        mapOf(
                            Key.SVAR_KAFKA_KEY to KafkaKey(steg0.forespoerselId).toJson(),
                            Key.ORGNR_UNDERENHETER to setOf(steg0.forespoersel.orgnr).toJson(Orgnr.serializer()),
                        ),
                    ).toJson(),
        )
    }

    override fun utfoerSteg1(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
    ) {
        publisher.publish(
            key = steg0.forespoerselId,
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to BehovType.HENT_PERSONER.toJson(),
            Key.KONTEKST_ID to steg0.kontekstId.toJson(),
            Key.DATA to
                data
                    .plus(
                        mapOf(
                            Key.SVAR_KAFKA_KEY to KafkaKey(steg0.forespoerselId).toJson(),
                            Key.FNR_LISTE to setOf(steg0.forespoersel.fnr).toJson(Fnr.serializer()),
                        ),
                    ).toJson(),
        )
    }

    override fun utfoerSteg2(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
    ) {
        "Data hentet. Sender events for å opprette sak og oppgave.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        val orgNavn = steg1.orgnrMedNavn[steg0.forespoersel.orgnr] ?: ORG_NAVN_DEFAULT
        val sykmeldt = steg2.personer[steg0.forespoersel.fnr] ?: personDefault(steg0.forespoersel.fnr)

        publisher.publish(
            key = steg0.forespoerselId,
            Key.EVENT_NAME to EventName.SAK_OG_OPPGAVE_OPPRETT_REQUESTED.toJson(),
            Key.KONTEKST_ID to steg0.kontekstId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                    Key.FORESPOERSEL to steg0.forespoersel.toJson(Forespoersel.serializer()),
                    Key.SYKMELDT to sykmeldt.toJson(Person.serializer()),
                    Key.VIRKSOMHET to orgNavn.toJson(),
                    Key.SKAL_HA_PAAMINNELSE to steg0.skalHaPaaminnelse.toJson(Boolean.serializer()),
                ).toJson(),
        )
    }

    override fun Steg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@HentDataTilSakOgOppgaveService),
            Log.event(eventName),
            Log.kontekstId(kontekstId),
            Log.forespoerselId(forespoerselId),
        )

    override fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    ) {
        "Klarte ikke hente data for å opprette sak og oppgave pga. feil: '${fail.feilmelding}'".also {
            logger.error(it)
            sikkerLogger.error(it)
        }
    }
}

private const val ORG_NAVN_DEFAULT = "Arbeidsgiver"

private fun personDefault(fnr: Fnr): Person = Person(fnr, "Ukjent person")
