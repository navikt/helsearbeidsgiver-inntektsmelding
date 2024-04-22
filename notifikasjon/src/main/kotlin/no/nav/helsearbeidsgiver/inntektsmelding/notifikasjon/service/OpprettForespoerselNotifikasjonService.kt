package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.service

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.Person
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.composite.CompositeEventListener
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisKey
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

// TODO gjør om til ServiceRiver og ta over steg 1 fra OpprettOppgaveService og OpprettSakService
// mulighet: gi flagg til ServiceRiver som skrur av inaktiv-sjekk og heller oppretter notifikasjoner med default-verdier
class OpprettForespoerselNotifikasjonService(
    private val rapid: RapidsConnection,
    override val redisStore: RedisStore
) : CompositeEventListener() {

    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    // TODO start på denne, men lag egen event for å servicesteg
    override val event = EventName.FORESPOERSEL_MOTTATT
    override val startKeys = setOf(
        Key.FORESPOERSEL_ID,
        Key.ORGNRUNDERENHET,
        Key.IDENTITETSNUMMER
    )
    override val dataKeys = setOf(
        Key.VIRKSOMHETER,
        Key.PERSONER
    )

    override fun new(melding: Map<Key, JsonElement>) {
        "Mottok event ${EventName.FORESPOERSEL_MOTTATT}. Henter data for å opprette sak og oppgave.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)
        val orgnr = Key.ORGNRUNDERENHET.les(String.serializer(), melding)
        val fnr = Key.IDENTITETSNUMMER.les(String.serializer(), melding)

        rapid.publish(
            Key.EVENT_NAME to event.toJson(),
            Key.BEHOV to BehovType.VIRKSOMHET.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            Key.ORGNRUNDERENHETER to listOf(orgnr).toJson(String.serializer())
        )

        rapid.publish(
            Key.EVENT_NAME to event.toJson(),
            Key.BEHOV to BehovType.HENT_PERSONER.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            Key.FNR_LISTE to listOf(fnr).toJson(String.serializer())
        )
    }

    override fun inProgress(melding: Map<Key, JsonElement>) {
        // Har ingen mellomsteg
    }

    // TODO sjekk isFinished (fra Service-interface)
    override fun finalize(melding: Map<Key, JsonElement>) {
        "Data hentet for å opprette sak og oppgave. Sender events å starte opprettelse.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        val transaksjonId = Key.UUID.les(UuidSerializer, melding)
        val forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding)
        val orgnr = Key.ORGNRUNDERENHET.les(String.serializer(), melding)
        val fnr = Key.IDENTITETSNUMMER.les(String.serializer(), melding)
        val orgMap = Key.VIRKSOMHETER.les(MapSerializer(String.serializer(), String.serializer()), melding)
        val personMap = Key.PERSONER.les(personMapSerializer, melding)

        val orgNavn = orgnr.let {
            orgMap[it] ?: defaultOrgNavn()
        }
        val person = fnr.let {
            personMap[it] ?: defaultPerson(fnr)
        }

        rapid.publish(
            Key.EVENT_NAME to EventName.SAK_OPPRETT_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            Key.ORGNRUNDERENHET to orgnr.toJson(),
            Key.PERSONER to person.toJson(Person.serializer())
        )

        rapid.publish(
            Key.EVENT_NAME to EventName.OPPGAVE_OPPRETT_REQUESTED.toJson(),
            Key.UUID to transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to forespoerselId.toJson(),
            Key.ORGNRUNDERENHET to orgnr.toJson(),
            Key.VIRKSOMHET to orgNavn.toJson()
        )
    }

    override fun onError(melding: Map<Key, JsonElement>, fail: Fail) {
        val utloesendeBehov = Key.BEHOV.lesOrNull(BehovType.serializer(), fail.utloesendeMelding.toMap())

        val datafeil = when (utloesendeBehov) {
            BehovType.VIRKSOMHET -> {
                Key.VIRKSOMHET to defaultOrgNavn().toJson()
            }
            BehovType.HENT_PERSONER -> {
                val fnr = Key.IDENTITETSNUMMER.les(String.serializer(), melding)

                Key.PERSONER to mapOf(fnr to defaultPerson(fnr)).toJson(personMapSerializer)
            }
            else -> {
                null
            }
        }

        if (datafeil != null) {
            redisStore.set(
                RedisKey.of(fail.transaksjonId, datafeil.first),
                datafeil.second.toString()
            )

            val meldingMedDefault = mapOf(datafeil.first to datafeil.second).plus(melding)

            finalize(meldingMedDefault)
        } else {
            "Ukjent feil under henting av data for opprettelse av sak og oppgave.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
        }
    }
}

private fun defaultOrgNavn(): String =
    "Arbeidsgiver"

private fun defaultPerson(fnr: String): Person =
    Person(
        fnr = fnr,
        navn = "Ukjent person",
        foedselsdato = Person.foedselsdato(fnr)
    )
