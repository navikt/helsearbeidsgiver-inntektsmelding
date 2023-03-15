package no.nav.helsearbeidsgiver.inntektsmelding.dirigent

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.log.logger
import no.nav.helsearbeidsgiver.felles.log.loggerSikker
import no.nav.helsearbeidsgiver.felles.message.Løsninger
import no.nav.helsearbeidsgiver.felles.message.løsesAv
import no.nav.helsearbeidsgiver.felles.rapidsrivers.demandNested
import no.nav.helsearbeidsgiver.felles.rapidsrivers.publish
import no.nav.helsearbeidsgiver.felles.rapidsrivers.require
import no.nav.helsearbeidsgiver.felles.rapidsrivers.requireNested
import no.nav.helsearbeidsgiver.felles.serializers.UuidSerializer

class Dirigent(
    rapid: RapidsConnection,
    private val mockRedisStore: MockRedisStore
) : River.PacketListener {
    private val logger = logger()
    private val loggerSikker = loggerSikker()

    init {
        River(rapid).apply {
            validate { msg ->
                msg.demandNested(
                    // TODO BehovType --> MessageType?
                    Key.Nested(Key.LØSNING, Key.BEHOV) to { it.fromJson(BehovType.serializer()) }
                )
                msg.require(
                    Key.LØSNING to { it.jsonObject }
                )
                msg.requireNested(
                    Key.Nested(Key.LØSNING, Key.INITIATE_ID) to { it.fromJson(UuidSerializer) }
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val løsningJson = Key.LØSNING.fra(packet).jsonObject

        // TODO erstatt !!
        val behovType = Key.BEHOV.fra(løsningJson)!!.fromJson(BehovType.serializer())
        val initiateId = Key.INITIATE_ID.fra(løsningJson)!!.fromJson(UuidSerializer)

        "Behov: '$behovType'\nInitiateId: '$initiateId'".let {
            logger.info(it)
            loggerSikker.info("$it\nInnkommende løsning: $løsningJson")
        }

        // TODO hva hvis ikke finnes? da har api-behovet ikke blitt lagret (feil), blitt besvart eller timet ut
        val fullPlan = mockRedisStore.hentPlan(initiateId)!!

        // Hent total løsning så langt fra redis
        val totalLøsning = mockRedisStore.hentWipSvar(initiateId)

        // TODO hva om ny løsning er en failure?
        // Oppdater total løsning med nylig mottatt løsning, og lagre til redis
        val totalLøsningOppdatert = totalLøsning.plus(behovType.name to løsningJson)
            .also { mockRedisStore.lagreWipSvar(initiateId, it) }

        // Finn første ufullførte delplan
        val nåværendePlan = fullPlan.finnFørsteUfullførteDelplan(totalLøsning.løsteBehov())
        if (nåværendePlan == null) {
            "Ingen nåværende delplan funnet for id '$initiateId'.".let {
                logger.error(it)
                loggerSikker.error(
                    "$it\n" +
                        "Plan: $fullPlan\n" +
                        "Ny løsning for '$behovType': $løsningJson\n" +
                        "Tidligere løste behov: ${totalLøsning.løsteBehov()}"
                )
            }

            // TODO lagre endelig feil i redis?
        } else if (nåværendePlan.løsesAv(totalLøsningOppdatert.løsteBehov())) {
            // Hvis vi er ferdige med delplanen med nylig mottatt løsning, finn neste delplan
            val nestePlan = fullPlan.finnFørsteUfullførteDelplan(totalLøsningOppdatert.løsteBehov())

            if (nestePlan != null) {
                logger.info("Fant neste delplan: $nestePlan.")

                context.publishAll(nestePlan, totalLøsningOppdatert)
            } else {
                // Hvis vi ikke finner ny delplan, så er vi ferdige
                mockRedisStore.lagreEndeligSvar(initiateId, totalLøsningOppdatert)
                logger.info("Ingen neste delplan funnet, endelig svar lagret.")
            }
        }

        // Kommer du hit er vi midt i en delplan og trenger ikke gjøre noe annet enn å vente på neste delløsning.
    }
}

fun MessageContext.publishAll(plan: Set<BehovType>, totalLøsning: Løsninger) {
    plan.forEach {
        publish(
            Key.BEHOV to mapOf(
                Key.BEHOV.str to it.toJson(BehovType.serializer()),
                Key.SESSION.str to totalLøsning.toJson(Løsninger.serializer())
            ).toJson()
        )
    }
}

private fun Key.fra(json: JsonObject): JsonElement? =
    json[str]
