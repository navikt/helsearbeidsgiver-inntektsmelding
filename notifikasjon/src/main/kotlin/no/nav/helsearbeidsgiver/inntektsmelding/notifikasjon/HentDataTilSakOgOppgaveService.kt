package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.felles.domene.PersonDato
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.orgMapSerializer
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceMed2Steg
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

data class Steg0(
    val transaksjonId: UUID,
    val forespoerselId: UUID,
    val orgnr: Orgnr,
    val fnr: Fnr,
)

data class Steg1(
    val orgnrMedNavn: Map<Orgnr, String>,
)

data class Steg2(
    val personer: Map<Fnr, Person>,
)

class HentDataTilSakOgOppgaveService(
    private val rapid: RapidsConnection,
) : ServiceMed2Steg<Steg0, Steg1, Steg2>() {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.FORESPOERSEL_MOTTATT

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            transaksjonId = Key.UUID.les(UuidSerializer, melding),
            forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding),
            orgnr = Key.ORGNRUNDERENHET.les(Orgnr.serializer(), melding),
            fnr = Key.FNR.les(Fnr.serializer(), melding),
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

        rapid.publish(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to BehovType.HENT_VIRKSOMHET_NAVN.toJson(),
            Key.UUID to steg0.transaksjonId.toJson(),
            Key.DATA to
                data
                    .plus(
                        Key.ORGNR_UNDERENHETER to setOf(steg0.orgnr).toJson(Orgnr.serializer()),
                    ).toJson(),
        )
    }

    override fun utfoerSteg1(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
    ) {
        rapid
            .publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_PERSONER.toJson(),
                Key.UUID to steg0.transaksjonId.toJson(),
                Key.DATA to
                    data
                        .plus(
                            Key.FNR_LISTE to setOf(steg0.fnr).toJson(Fnr.serializer()),
                        ).toJson(),
            )
    }

    override fun utfoerSteg2(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
    ) {
        "Data hentet for å opprette sak og oppgave. Sender events å starte opprettelse.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        val orgNavn = steg1.orgnrMedNavn[steg0.orgnr] ?: ORG_NAVN_DEFAULT
        val sykmeldt = steg2.personer[steg0.fnr] ?: personDefault(steg0.fnr)

        val sykmeldtPersonDato =
            PersonDato(
                navn = sykmeldt.navn,
                fødselsdato = sykmeldt.foedselsdato,
                ident = sykmeldt.fnr.verdi,
            )

        rapid.publish(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to BehovType.OPPRETT_SAK.toJson(),
            Key.UUID to steg0.transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
            Key.ORGNRUNDERENHET to steg0.orgnr.toJson(),
            // Send midlertidig, skal erstattes av Key.PERSONER
            Key.ARBEIDSTAKER_INFORMASJON to sykmeldtPersonDato.toJson(PersonDato.serializer()),
            Key.PERSONER to sykmeldt.toJson(Person.serializer()),
        )

        rapid.publish(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to BehovType.OPPRETT_OPPGAVE.toJson(),
            Key.UUID to steg0.transaksjonId.toJson(),
            Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
            Key.ORGNRUNDERENHET to steg0.orgnr.toJson(),
            Key.VIRKSOMHET to orgNavn.toJson(),
        )
    }

    override fun Steg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@HentDataTilSakOgOppgaveService),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
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

private fun personDefault(fnr: Fnr): Person =
    Person(
        fnr = fnr,
        navn = "Ukjent person",
        foedselsdato = Person.foedselsdato(fnr),
    )
