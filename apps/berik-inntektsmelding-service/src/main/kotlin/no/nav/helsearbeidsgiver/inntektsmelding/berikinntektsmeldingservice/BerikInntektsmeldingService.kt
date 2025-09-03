package no.nav.helsearbeidsgiver.inntektsmelding.berikinntektsmeldingservice

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.BehovType
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.Forespoersel
import no.nav.hag.simba.utils.felles.domene.Person
import no.nav.hag.simba.utils.felles.json.les
import no.nav.hag.simba.utils.felles.json.lesOrNull
import no.nav.hag.simba.utils.felles.json.orgMapSerializer
import no.nav.hag.simba.utils.felles.json.personMapSerializer
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.felles.model.Fail
import no.nav.hag.simba.utils.felles.utils.Log
import no.nav.hag.simba.utils.rr.KafkaKey
import no.nav.hag.simba.utils.rr.Publisher
import no.nav.hag.simba.utils.rr.service.ServiceMed3Steg
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.api.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateTimeSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.time.LocalDateTime
import java.util.UUID

private const val UKJENT_NAVN = "Ukjent navn"
private const val UKJENT_VIRKSOMHET = "Ukjent virksomhet"

data class Steg0(
    val kontekstId: UUID,
    val avsenderFnr: Fnr?, // TODO: trenger ikke nullable når / om vi lager egen service for API-innsendt
    val forespoersel: Forespoersel,
    val inntektsmeldingId: UUID,
    val skjema: SkjemaInntektsmelding,
    val innsending: Innsending?, // TODO: Kan dele opp API-innsending-berik i egen service
    val mottatt: LocalDateTime,
)

data class Steg1(
    val orgnrMedNavn: Map<Orgnr, String>,
)

data class Steg2(
    val personer: Map<Fnr, Person>,
)

data class Steg3(
    val inntektsmelding: Inntektsmelding,
    val erDuplikat: Boolean,
)

class BerikInntektsmeldingService(
    private val publisher: Publisher,
) : ServiceMed3Steg<Steg0, Steg1, Steg2, Steg3>() {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.INNTEKTSMELDING_SKJEMA_LAGRET

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            kontekstId = Key.KONTEKST_ID.les(UuidSerializer, melding),
            avsenderFnr = Key.ARBEIDSGIVER_FNR.lesOrNull(Fnr.serializer(), melding),
            forespoersel = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding),
            inntektsmeldingId = Key.INNTEKTSMELDING_ID.les(UuidSerializer, melding),
            skjema = Key.SKJEMA_INNTEKTSMELDING.les(SkjemaInntektsmelding.serializer(), melding),
            innsending = Key.INNSENDING.lesOrNull(Innsending.serializer(), melding),
            mottatt = Key.MOTTATT.les(LocalDateTimeSerializer, melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            orgnrMedNavn = Key.VIRKSOMHETER.les(orgMapSerializer, melding),
        )

    override fun lesSteg2(melding: Map<Key, JsonElement>): Steg2 =
        Steg2(
            personer = Key.PERSONER.les(personMapSerializer, melding),
        )

    override fun lesSteg3(melding: Map<Key, JsonElement>): Steg3 =
        Steg3(
            inntektsmelding = Key.INNTEKTSMELDING.les(Inntektsmelding.serializer(), melding),
            erDuplikat = Key.ER_DUPLIKAT_IM.les(Boolean.serializer(), melding),
        )

    override fun utfoerSteg0(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
    ) {
        publisher
            .publish(
                key = steg0.skjema.forespoerselId,
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_VIRKSOMHET_NAVN.toJson(),
                Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                Key.DATA to
                    data
                        .plus(
                            mapOf(
                                Key.SVAR_KAFKA_KEY to KafkaKey(steg0.skjema.forespoerselId).toJson(),
                                Key.ORGNR_UNDERENHETER to setOf(steg0.forespoersel.orgnr).toJson(Orgnr.serializer()),
                            ),
                        ).toJson(),
            ).also { loggBehovPublisert(BehovType.HENT_VIRKSOMHET_NAVN, it) }
    }

    override fun utfoerSteg1(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
    ) {
        publisher
            .publish(
                key = steg0.skjema.forespoerselId,
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_PERSONER.toJson(),
                Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                Key.DATA to
                    data
                        .plus(
                            mapOf(
                                Key.SVAR_KAFKA_KEY to KafkaKey(steg0.skjema.forespoerselId).toJson(),
                                Key.FNR_LISTE to
                                    listOfNotNull(
                                        steg0.forespoersel.fnr,
                                        steg0.avsenderFnr,
                                    ).toJson(Fnr.serializer()),
                            ),
                        ).toJson(),
            ).also { loggBehovPublisert(BehovType.HENT_PERSONER, it) }
    }

    override fun utfoerSteg2(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
    ) {
        val orgNavn = steg1.orgnrMedNavn[steg0.forespoersel.orgnr] ?: UKJENT_VIRKSOMHET
        val sykmeldtNavn = steg2.personer[steg0.forespoersel.fnr]?.navn ?: UKJENT_NAVN
        val avsenderNavn = steg2.personer[steg0.avsenderFnr]?.navn ?: UKJENT_NAVN
        val aarsakInnsending = if (steg0.forespoersel.erBesvart) AarsakInnsending.Endring else AarsakInnsending.Ny // !!! hmm

        val inntektsmelding =
            mapInntektsmelding(
                innsending = steg0.innsending,
                inntektsmeldingId = steg0.inntektsmeldingId,
                forespoersel = steg0.forespoersel,
                skjema = steg0.skjema,
                aarsakInnsending = aarsakInnsending,
                virksomhetNavn = orgNavn,
                sykmeldtNavn = sykmeldtNavn,
                avsenderNavn = avsenderNavn,
                mottatt = steg0.mottatt,
            )

        publisher
            .publish(
                key = steg0.skjema.forespoerselId,
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.LAGRE_IM.toJson(),
                Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                Key.DATA to
                    data
                        .plus(
                            Key.INNTEKTSMELDING to inntektsmelding.toJson(Inntektsmelding.serializer()),
                        ).toJson(),
            ).also { loggBehovPublisert(BehovType.LAGRE_IM, it) }
    }

    override fun utfoerSteg3(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
        steg3: Steg3,
    ) {
        if (!steg3.erDuplikat) {
            val publisert =
                publisher.publish(
                    key = steg0.skjema.forespoerselId,
                    Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
                    Key.KONTEKST_ID to steg0.kontekstId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.FORESPOERSEL_ID to steg0.skjema.forespoerselId.toJson(),
                            Key.INNTEKTSMELDING to steg3.inntektsmelding.toJson(Inntektsmelding.serializer()),
                        ).toJson(),
                )

            MdcUtils.withLogFields(
                Log.event(EventName.INNTEKTSMELDING_MOTTATT),
            ) {
                logger.info("Publiserte melding.")
                sikkerLogger.info("Publiserte melding:\n${publisert.toPretty()}")
            }
        }
    }

    override fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    ) {
        // FeilLytter plukker opp og retryer feil som inneholder eventet INNTEKTSMELDING_SKJEMA_LAGRET.
    }

    override fun Steg0.loggfelt(): Map<String, String> =
        mapOf(
            Log.klasse(this@BerikInntektsmeldingService),
            Log.event(eventName),
            Log.kontekstId(kontekstId),
            Log.inntektsmeldingId(inntektsmeldingId),
            Log.forespoerselId(skjema.forespoerselId),
        )

    private fun loggBehovPublisert(
        behovType: BehovType,
        publisert: JsonElement,
    ) {
        MdcUtils.withLogFields(
            Log.behov(behovType),
        ) {
            "Publiserte melding med behov $behovType.".let {
                logger.info(it)
                sikkerLogger.info("$it\n${publisert.toPretty()}")
            }
        }
    }
}
