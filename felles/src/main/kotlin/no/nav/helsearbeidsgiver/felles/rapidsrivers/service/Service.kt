package no.nav.helsearbeidsgiver.felles.rapidsrivers.service

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.rapidsrivers.model.Fail
import no.nav.helsearbeidsgiver.felles.rapidsrivers.redis.RedisStore
import no.nav.helsearbeidsgiver.utils.log.MdcUtils
import org.slf4j.Logger

sealed interface Service {
    interface MedRedis {
        val redisStore: RedisStore
    }

    val eventName: EventName

    // TODO internal?
    fun onData(melding: Map<Key, JsonElement>)

    // TODO internal?
    fun onError(
        melding: Map<Key, JsonElement>,
        fail: Fail,
    )
}

// TODO lese påkrevde felt som transaksjonId her?
abstract class ServiceMed1Steg<S0, S1> : Service {
    protected abstract val logger: Logger
    protected abstract val sikkerLogger: Logger

    protected abstract fun lesSteg0(melding: Map<Key, JsonElement>): S0

    protected abstract fun lesSteg1(melding: Map<Key, JsonElement>): S1

    protected abstract fun utfoerSteg0(
        data: Map<Key, JsonElement>,
        steg0: S0,
    )

    protected abstract fun utfoerSteg1(
        data: Map<Key, JsonElement>,
        steg0: S0,
        steg1: S1,
    )

    protected abstract fun S0.loggfelt(): Map<String, String>

    override fun onData(melding: Map<Key, JsonElement>) {
        runCatching {
            Triple(
                first = lesData(melding),
                second = lesSteg0(melding),
                third = lesSteg1(melding),
            )
        }.onSuccess {
            medLoggfelt(it.second) {
                utfoerSteg1(it.first, it.second, it.third)
            }
        }.onFailure {
            lesOgUtfoerSteg0(melding)
        }
    }

    private fun lesOgUtfoerSteg0(melding: Map<Key, JsonElement>) {
        runCatching {
            Pair(
                first = lesData(melding),
                second = lesSteg0(melding),
            )
        }.onSuccess {
            medLoggfelt(it.second) {
                utfoerSteg0(it.first, it.second)
            }
        }.onFailure {
            "Klarte ikke lese startdata for service.".also {
                logger.error(it)
                sikkerLogger.error(it)
            }
        }
    }

    internal fun lesData(melding: Map<Key, JsonElement>): Map<Key, JsonElement> = melding[Key.DATA]?.toMap().orEmpty()

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
        data: Map<Key, JsonElement>,
        steg0: S0,
        steg1: S1,
        steg2: S2,
    )

    override fun onData(melding: Map<Key, JsonElement>) {
        runCatching {
            Quadruple(
                first = lesData(melding),
                second = lesSteg0(melding),
                third = lesSteg1(melding),
                fourth = lesSteg2(melding),
            )
        }.onSuccess {
            medLoggfelt(it.second) {
                utfoerSteg2(it.first, it.second, it.third, it.fourth)
            }
        }.onFailure {
            super.onData(melding)
        }
    }
}

abstract class ServiceMed3Steg<S0, S1, S2, S3> : ServiceMed2Steg<S0, S1, S2>() {
    protected abstract fun lesSteg3(melding: Map<Key, JsonElement>): S3

    protected abstract fun utfoerSteg3(
        data: Map<Key, JsonElement>,
        steg0: S0,
        steg1: S1,
        steg2: S2,
        steg3: S3,
    )

    override fun onData(melding: Map<Key, JsonElement>) {
        runCatching {
            Quintuple(
                first = lesData(melding),
                second = lesSteg0(melding),
                third = lesSteg1(melding),
                fourth = lesSteg2(melding),
                fifth = lesSteg3(melding),
            )
        }.onSuccess {
            medLoggfelt(it.second) {
                utfoerSteg3(it.first, it.second, it.third, it.fourth, it.fifth)
            }
        }.onFailure {
            super.onData(melding)
        }
    }
}

abstract class ServiceMed4Steg<S0, S1, S2, S3, S4> : ServiceMed3Steg<S0, S1, S2, S3>() {
    protected abstract fun lesSteg4(melding: Map<Key, JsonElement>): S4

    protected abstract fun utfoerSteg4(
        data: Map<Key, JsonElement>,
        steg0: S0,
        steg1: S1,
        steg2: S2,
        steg3: S3,
        steg4: S4,
    )

    override fun onData(melding: Map<Key, JsonElement>) {
        runCatching {
            Sextuple(
                first = lesData(melding),
                second = lesSteg0(melding),
                third = lesSteg1(melding),
                fourth = lesSteg2(melding),
                fifth = lesSteg3(melding),
                sixth = lesSteg4(melding),
            )
        }.onSuccess {
            medLoggfelt(it.second) {
                utfoerSteg4(it.first, it.second, it.third, it.fourth, it.fifth, it.sixth)
            }
        }.onFailure {
            super.onData(melding)
        }
    }
}

abstract class ServiceMed5Steg<S0, S1, S2, S3, S4, S5> : ServiceMed4Steg<S0, S1, S2, S3, S4>() {
    protected abstract fun lesSteg5(melding: Map<Key, JsonElement>): S5

    protected abstract fun utfoerSteg5(
        data: Map<Key, JsonElement>,
        steg0: S0,
        steg1: S1,
        steg2: S2,
        steg3: S3,
        steg4: S4,
        steg5: S5,
    )

    override fun onData(melding: Map<Key, JsonElement>) {
        runCatching {
            Septuple(
                first = lesData(melding),
                second = lesSteg0(melding),
                third = lesSteg1(melding),
                fourth = lesSteg2(melding),
                fifth = lesSteg3(melding),
                sixth = lesSteg4(melding),
                seventh = lesSteg5(melding),
            )
        }.onSuccess {
            medLoggfelt(it.second) {
                utfoerSteg5(it.first, it.second, it.third, it.fourth, it.fifth, it.sixth, it.seventh)
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

private class Sextuple<S0, S1, S2, S3, S4, S5>(
    val first: S0,
    val second: S1,
    val third: S2,
    val fourth: S3,
    val fifth: S4,
    val sixth: S5,
)

private class Septuple<S0, S1, S2, S3, S4, S5, S6>(
    val first: S0,
    val second: S1,
    val third: S2,
    val fourth: S3,
    val fifth: S4,
    val sixth: S5,
    val seventh: S6,
)
