package no.nav.helsearbeidsgiver.inntektsmelding.berikinntektsmeldingservice

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.domene.inntektsmelding.Utils.convert
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.skjema.SkjemaInntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Forespoersel
import no.nav.helsearbeidsgiver.felles.domene.Person
import no.nav.helsearbeidsgiver.felles.json.les
import no.nav.helsearbeidsgiver.felles.json.orgMapSerializer
import no.nav.helsearbeidsgiver.felles.json.personMapSerializer
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.service.ServiceMed4Steg
import no.nav.helsearbeidsgiver.felles.utils.Log
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

private const val UKJENT_NAVN = "Ukjent navn"
private const val UKJENT_VIRKSOMHET = "Ukjent virksomhet"

data class Steg0(
    val transaksjonId: UUID,
    val avsenderFnr: Fnr,
    val skjema: SkjemaInntektsmelding,
    val innsendingId: Long,
)

data class Steg1(
    val forespoersel: Forespoersel,
)

data class Steg2(
    val orgnrMedNavn: Map<Orgnr, String>,
)

data class Steg3(
    val personer: Map<Fnr, Person>,
)

data class Steg4(
    val inntektsmelding: Inntektsmelding,
    val erDuplikat: Boolean,
)

class BerikInntektsmeldingService(
    private val rapid: RapidsConnection,
) : ServiceMed4Steg<Steg0, Steg1, Steg2, Steg3, Steg4>() {
    override val logger = logger()
    override val sikkerLogger = sikkerLogger()

    override val eventName = EventName.INNTEKTSMELDING_SKJEMA_LAGRET

    override fun lesSteg0(melding: Map<Key, JsonElement>): Steg0 =
        Steg0(
            transaksjonId = Key.UUID.les(UuidSerializer, melding),
            avsenderFnr = Key.ARBEIDSGIVER_FNR.les(Fnr.serializer(), melding),
            skjema = Key.SKJEMA_INNTEKTSMELDING.les(SkjemaInntektsmelding.serializer(), melding),
            innsendingId = Key.INNSENDING_ID.les(Long.serializer(), melding),
        )

    override fun lesSteg1(melding: Map<Key, JsonElement>): Steg1 =
        Steg1(
            forespoersel = Key.FORESPOERSEL_SVAR.les(Forespoersel.serializer(), melding),
        )

    override fun lesSteg2(melding: Map<Key, JsonElement>): Steg2 =
        Steg2(
            orgnrMedNavn = Key.VIRKSOMHETER.les(orgMapSerializer, melding),
        )

    override fun lesSteg3(melding: Map<Key, JsonElement>): Steg3 =
        Steg3(
            personer = Key.PERSONER.les(personMapSerializer, melding),
        )

    override fun lesSteg4(melding: Map<Key, JsonElement>): Steg4 =
        Steg4(
            inntektsmelding = Key.INNTEKTSMELDING.les(Inntektsmelding.serializer(), melding),
            erDuplikat = Key.ER_DUPLIKAT_IM.les(Boolean.serializer(), melding),
        )

    override fun utfoerSteg0(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
    ) {
        rapid
            .publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_TRENGER_IM.toJson(),
                Key.UUID to steg0.transaksjonId.toJson(),
                Key.DATA to
                    data
                        .plus(Key.FORESPOERSEL_ID to steg0.skjema.forespoerselId.toJson())
                        .toJson(),
            ).also { loggBehovPublisert(BehovType.HENT_TRENGER_IM, it) }
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
                        .plus(Key.ORGNR_UNDERENHETER to setOf(steg1.forespoersel.orgnr).toJson(String.serializer()))
                        .toJson(),
            ).also { loggBehovPublisert(BehovType.HENT_VIRKSOMHET_NAVN, it) }
    }

    override fun utfoerSteg2(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
    ) {
        rapid
            .publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.HENT_PERSONER.toJson(),
                Key.UUID to steg0.transaksjonId.toJson(),
                Key.DATA to
                    data
                        .plus(
                            Key.FNR_LISTE to
                                listOf(
                                    steg1.forespoersel.fnr.let(::Fnr),
                                    steg0.avsenderFnr,
                                ).toJson(Fnr.serializer()),
                        ).toJson(),
            ).also { loggBehovPublisert(BehovType.HENT_PERSONER, it) }
    }

    override fun utfoerSteg3(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
        steg3: Steg3,
    ) {
        val orgNavn = steg2.orgnrMedNavn[steg1.forespoersel.orgnr.let(::Orgnr)] ?: UKJENT_VIRKSOMHET
        val sykmeldtNavn = steg3.personer[steg1.forespoersel.fnr.let(::Fnr)]?.navn ?: UKJENT_NAVN
        val avsenderNavn = steg3.personer[steg0.avsenderFnr]?.navn ?: UKJENT_NAVN
        val aarsakInnsending = if (steg1.forespoersel.erBesvart) AarsakInnsending.Endring else AarsakInnsending.Ny

        val inntektsmelding =
            mapInntektsmelding(
                forespoersel = steg1.forespoersel,
                skjema = steg0.skjema,
                aarsakInnsending = aarsakInnsending,
                virksomhetNavn = orgNavn,
                sykmeldtNavn = sykmeldtNavn,
                avsenderNavn = avsenderNavn,
            )

        val bestemmendeFravaersdag = utledBestemmendeFravaersdag(steg1.forespoersel, inntektsmelding)

        val inntektsdato = inntektsmelding.inntekt?.inntektsdato
        if (inntektsdato != null && bestemmendeFravaersdag.isBefore(inntektsdato)) {
            "Bestemmende fraværsdag er før inntektsdato. Dette er ikke mulig. Spleis vil trolig spør om ny inntektsmelding.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
        }

        val inntektsmeldingGammeltFormat = inntektsmelding.convert().copy(bestemmendeFraværsdag = bestemmendeFravaersdag)

        rapid
            .publish(
                Key.EVENT_NAME to eventName.toJson(),
                Key.BEHOV to BehovType.LAGRE_IM.toJson(),
                Key.UUID to steg0.transaksjonId.toJson(),
                Key.DATA to
                    data
                        .plus(
                            mapOf(
                                Key.FORESPOERSEL_ID to steg0.skjema.forespoerselId.toJson(),
                                // TODO Fjern Key.INNTEKTSMELDING etter overgangsperiode
                                Key.INNTEKTSMELDING to inntektsmeldingGammeltFormat.toJson(Inntektsmelding.serializer()),
                                Key.INNTEKTSMELDING_DOKUMENT to inntektsmeldingGammeltFormat.toJson(Inntektsmelding.serializer()),
                                Key.INNSENDING_ID to steg0.innsendingId.toJson(Long.serializer()),
                            ),
                        ).toJson(),
            ).also { loggBehovPublisert(BehovType.LAGRE_IM, it) }
    }

    override fun utfoerSteg4(
        data: Map<Key, JsonElement>,
        steg0: Steg0,
        steg1: Steg1,
        steg2: Steg2,
        steg3: Steg3,
        steg4: Steg4,
    ) {
        if (!steg4.erDuplikat) {
            val publisert =
                rapid.publish(
                    Key.EVENT_NAME to EventName.INNTEKTSMELDING_MOTTATT.toJson(),
                    Key.UUID to steg0.transaksjonId.toJson(),
                    Key.FORESPOERSEL_ID to steg0.skjema.forespoerselId.toJson(),
                    Key.INNTEKTSMELDING_DOKUMENT to steg4.inntektsmelding.toJson(Inntektsmelding.serializer()),
                    Key.INNSENDING_ID to steg0.innsendingId.toJson(Long.serializer()),
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
            Log.transaksjonId(transaksjonId),
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
