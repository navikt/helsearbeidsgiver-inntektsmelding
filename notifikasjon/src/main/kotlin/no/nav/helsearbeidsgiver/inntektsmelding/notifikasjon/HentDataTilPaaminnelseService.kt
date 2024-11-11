package no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.orgMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceMed2Steg
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class HentDataTilPaaminnelseService(
    private val rapid: RapidsConnection,
) : ServiceMed2Steg<HentDataTilPaaminnelseService.Steg0, HentDataTilPaaminnelseService.Steg1, HentDataTilPaaminnelseService.Steg2>() {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.MANUELL_ENDRE_PAAMINNELSE

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            transaksjonId = Key.UUID.les(UuidSerializer, melding),
            forespoerselId = Key.FORESPOERSEL_ID.les(UuidSerializer, melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            forespoersel = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding),
        )

    override fun lesSteg2(melding: Map<Key, JsonElement>): Steg2 =
        Steg2(
            orgnrMedNavn = Key.VIRKSOMHETER.les(orgMapSerializer, melding),
        )

    override fun utfoerSteg0(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
    ) {
        "Mottok event $eventName. Henter data for endre påminnelse på oppgave.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        rapid.publish(
            Key.EVENT_NAME to eventName.toJson(),
            Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
            Key.UUID to steg0.transaksjonId.toJson(),
            Key.DATA to
                data
                    .plus(
                        Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
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
                Key.BEHOV to BehovType.HENT_VIRKSOMHET_NAVN.toJson(),
                Key.UUID to steg0.transaksjonId.toJson(),
                Key.DATA to
                    data
                        .plus(
                            Key.ORGNR_UNDERENHETER to setOf(steg1.forespoersel.orgnr).toJson(String.serializer()),
                        ).toJson(),
            )
    }

    override fun utfoerSteg2(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
    ) {
        "Data hentet. Sender event for å endre påminnelse på oppgave.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        val orgNavn = steg2.orgnrMedNavn[steg1.forespoersel.orgnr.let(::Orgnr)] ?: ORG_NAVN_DEFAULT

        rapid.publish(
            Key.EVENT_NAME to EventName.OPPGAVE_ENDRE_PAAMINNELSE_REQUESTED.toJson(),
            Key.UUID to steg0.transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FORESPOERSEL_ID to steg0.forespoerselId.toJson(),
                    Key.FORESPOERSEL to steg1.forespoersel.toJson(Forespoersel.serializer()),
                    Key.VIRKSOMHET to orgNavn.toJson(),
                ).toJson(),
        )
    }

    override fun Steg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@HentDataTilPaaminnelseService),
            Log.event(eventName),
            Log.transaksjonId(transaksjonId),
            Log.forespoerselId(forespoerselId),
        )

    override fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    ) {
        "Klarte ikke hente data for å endre påminnelse på oppgave pga. feil: '${fail.feilmelding}'".also {
            logger.error(it)
            sikkerLogger.error(it)
        }
    }

    data class Steg0(
        val transaksjonId: UUID,
        val forespoerselId: UUID,
    )

    data class Steg1(
        val forespoersel: Forespoersel,
    )

    data class Steg2(
        val orgnrMedNavn: Map<Orgnr, String>,
    )
}

private const val ORG_NAVN_DEFAULT = "Arbeidsgiver"
