package no.nav.helsearbeidsgiver.felles.rapidsrivers.service

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import org.slf4j.Logger

// TODO kan bli sealed når alle servicer bruker steg-abstraksjon
abstract class Service {
    abstract val redisStore: RedisStore
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
    protected abstract val logger: Logger
    protected abstract val sikkerLogger: Logger

    protected abstract fun lesSteg0(melding: Map<Key, JsonElement>): S0

    protected abstract fun lesSteg1(melding: Map<Key, JsonElement>): S1

    protected abstract fun utfoerSteg0(steg0: S0)

    protected abstract fun utfoerSteg1(
        steg0: S0,
        steg1: S1,
    )

    protected abstract fun S0.loggfelt(): Map<String, String>

    override fun onData(melding: Map<Key, JsonElement>) {
        runCatching {
            Pair(
                first = lesSteg0(melding),
                second = lesSteg1(melding),
            )
        }.onSuccess {
            medLoggfelt(it.first) {
                utfoerSteg1(it.first, it.second)
            }
        }.onFailure {
            lesOgUtfoerSteg0(melding)
        }
    }

    private fun lesOgUtfoerSteg0(melding: Map<Key, JsonElement>) {
        runCatching { lesSteg0(melding) }
            .onSuccess {
                medLoggfelt(it) {
                    utfoerSteg0(it)
                }
            }.onFailure {
                "Klarte ikke lese startdata for service.".also {
                    logger.error(it)
                    sikkerLogger.error(it)
                }
            }
    }

    internal fun medLoggfelt(
        steg0: S0,
        block: () -> Unit,
    ) {
        MdcUtils.withLogFields(
            *steg0.loggfelt().toList().toTypedArray(),
        ) {
            block()
        }
    }
}

abstract class ServiceMed2Steg<S0, S1, S2> : ServiceMed1Steg<S0, S1>() {
    protected abstract fun lesSteg2(melding: Map<Key, JsonElement>): S2

    protected abstract fun utfoerSteg2(
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
            medLoggfelt(it.first) {
                utfoerSteg2(it.first, it.second, it.third)
            }
        }.onFailure {
            super.onData(melding)
        }
    }
}

abstract class ServiceMed3Steg<S0, S1, S2, S3> : ServiceMed2Steg<S0, S1, S2>() {
    protected abstract fun lesSteg3(melding: Map<Key, JsonElement>): S3

    protected abstract fun utfoerSteg3(
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
            medLoggfelt(it.first) {
                utfoerSteg3(it.first, it.second, it.third, it.fourth)
            }
        }.onFailure {
            super.onData(melding)
        }
    }
}

abstract class ServiceMed4Steg<S0, S1, S2, S3, S4> : ServiceMed3Steg<S0, S1, S2, S3>() {
    protected abstract fun lesSteg4(melding: Map<Key, JsonElement>): S4

    protected abstract fun utfoerSteg4(
        steg0: S0,
        steg1: S1,
        steg2: S2,
        steg3: S3,
        steg4: S4,
    )

    override fun onData(melding: Map<Key, JsonElement>) {
        runCatching {
            Quintuple(
                first = lesSteg0(melding),
                second = lesSteg1(melding),
                third = lesSteg2(melding),
                fourth = lesSteg3(melding),
                fifth = lesSteg4(melding),
            )
        }.onSuccess {
            medLoggfelt(it.first) {
                utfoerSteg4(it.first, it.second, it.third, it.fourth, it.fifth)
            }
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

private class Quintuple<S0, S1, S2, S3, S4>(
    val first: S0,
    val second: S1,
    val third: S2,
    val fourth: S3,
    val fifth: S4,
)
