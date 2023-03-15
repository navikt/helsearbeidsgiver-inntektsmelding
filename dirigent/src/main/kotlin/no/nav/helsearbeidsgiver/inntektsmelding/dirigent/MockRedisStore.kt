package no.nav.helsearbeidsgiver.inntektsmelding.dirigent

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.RedisKey
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.parseJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.log.logger
import no.nav.helsearbeidsgiver.felles.log.loggerSikker
import no.nav.helsearbeidsgiver.felles.message.Løsninger
import no.nav.helsearbeidsgiver.felles.message.Plan
import java.util.UUID

@Suppress("UNUSED_PARAMETER")
class MockRedisStore(redisUrl: String) {
    private val logger = logger()
    private val loggerSikker = loggerSikker()

    private val mockRedis = mutableMapOf<String, String>()

    fun shutdown() {
        loggerSikker.info("Shutdown mock redis store.")
    }

    fun hentPlan(id: UUID): Plan? =
        redisKeyPlan(id)
            .get()
            ?.fromJson(Plan.serializer())

    fun hentWipSvar(id: UUID): Løsninger =
        redisKeyWip(id)
            .get()
            ?.fromJson(Løsninger.serializer())
            ?: Løsninger(emptyMap())
                .also { logger.warn("Fant ikke WIP for id '$id'.") }

    fun lagrePlan(id: UUID, plan: Plan) {
        redisKeyPlan(id)
            .set(plan.toJson(Plan.serializer()))
    }

    fun lagreWipSvar(id: UUID, totalLøsning: Løsninger): RedisKey =
        redisKeyWip(id)
            .set(totalLøsning.toJson(Løsninger.serializer()))

    fun lagreEndeligSvar(id: UUID, totalLøsning: Løsninger): RedisKey =
        RedisKey(id)
            .set(totalLøsning.toJson(Løsninger.serializer()))

    private fun RedisKey.get(): JsonElement? =
        mockRedis[verdi]?.parseJson()

    private fun RedisKey.set(json: JsonElement): RedisKey =
        also {
            loggerSikker.info("Sett nøkkel '$verdi' til verdi:\n$json")
            mockRedis[verdi] = json.toString()
        }
}

private fun redisKeyWip(id: UUID): RedisKey =
    "wip|$id".let(::RedisKey)

private fun redisKeyPlan(id: UUID): RedisKey =
    "plan|$id".let(::RedisKey)
