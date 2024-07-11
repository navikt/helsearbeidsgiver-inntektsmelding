package no.nav.helsearbeidsgiver.felles.rapidsrivers.service

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStoreClassSpecific
import org.slf4j.Logger

// TODO kan bli sealed når alle servicer bruker steg-abstraksjon
abstract class Service {
    abstract val redisStore: RedisStoreClassSpecific
    abstract val eventName: EventName
    abstract val startKeys: Set<Key>
    abstract val dataKeys: Set<Key>

    // TODO kan bli internal når alle servicer bruker steg-abstraksjon
    abstract fun onData(melding: Map<Key, JsonElement>)

    abstract fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    )

    fun isFinished(melding: Map<Key, JsonElement>): Boolean = dataKeys.all(melding::containsKey)

    internal fun isInactive(redisData: Map<Key, JsonElement>): Boolean = !startKeys.all(redisData::containsKey)
}

// TODO lese påkrevde felt som transaksjonId her?
abstract class ServiceMed1Steg<S0, S1> : Service() {
    abstract val logger: Logger
    abstract val sikkerLogger: Logger

    abstract fun lesSteg0(melding: Map<Key, JsonElement>): S0

    abstract fun lesSteg1(melding: Map<Key, JsonElement>): S1

    abstract fun utfoerSteg0(steg0: S0)

    abstract fun utfoerSteg1(
        steg0: S0,
        steg1: S1,
    )

    override fun onData(melding: Map<Key, JsonElement>) {
        runCatching {
            Pair(
                first = lesSteg0(melding),
                second = lesSteg1(melding),
            )
        }.onSuccess {
            utfoerSteg1(it.first, it.second)
        }.onFailure {
            lesOgUtfoerSteg0(melding)
        }
    }

    private fun lesOgUtfoerSteg0(melding: Map<Key, JsonElement>) {
        runCatching { lesSteg0(melding) }
            .onSuccess {
                utfoerSteg0(it)
            }.onFailure {
                "Klarte ikke lese startdata for service.".also {
                    logger.error(it)
                    sikkerLogger.error(it)
                }
            }
    }
}

abstract class ServiceMed2Steg<S0, S1, S2> : ServiceMed1Steg<S0, S1>() {
    abstract fun lesSteg2(melding: Map<Key, JsonElement>): S2

    abstract fun utfoerSteg2(
        steg0: S0,
        steg1: S1,
        steg2: S2,
    )

    override fun onData(melding: Map<Key, JsonElement>) {
        runCatching {
            Triple(
                first = lesSteg0(melding),
                second = lesSteg1(melding),
                third = lesSteg2(melding),
            )
        }.onSuccess {
            utfoerSteg2(it.first, it.second, it.third)
        }.onFailure {
            super.onData(melding)
        }
    }
}

abstract class ServiceMed3Steg<S0, S1, S2, S3> : ServiceMed2Steg<S0, S1, S2>() {
    abstract fun lesSteg3(melding: Map<Key, JsonElement>): S3

    abstract fun utfoerSteg3(
        steg0: S0,
        steg1: S1,
        steg2: S2,
        steg3: S3,
    )

    override fun onData(melding: Map<Key, JsonElement>) {
        runCatching {
            Quadruple(
                first = lesSteg0(melding),
                second = lesSteg1(melding),
                third = lesSteg2(melding),
                fourth = lesSteg3(melding),
            )
        }.onSuccess {
            utfoerSteg3(it.first, it.second, it.third, it.fourth)
        }.onFailure {
            super.onData(melding)
        }
    }
}

private class Quadruple<S0, S1, S2, S3>(
    val first: S0,
    val second: S1,
    val third: S2,
    val fourth: S3,
)
