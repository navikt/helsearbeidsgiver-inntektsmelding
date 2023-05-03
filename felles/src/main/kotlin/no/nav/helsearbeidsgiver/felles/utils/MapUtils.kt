package no.nav.helsearbeidsgiver.felles.utils

fun <K : Any, V : Any, R : Any> Map<K, V>.mapKeysNotNull(transform: (K) -> R?) =
    mapNotNull { (key, value) ->
        transform(key)
            ?.to(value)
    }
        .toMap()

fun <K : Any, V : Any, R : Any> Map<K, V>.mapValuesNotNull(transform: (V) -> R?) =
    mapNotNull { (key, value) ->
        transform(value)
            ?.let { key to it }
    }
        .toMap()

fun <K : Any, V : Any> mapOfNotNull(vararg pair: Pair<K, V?>): Map<K, V> = mapOf(*pair).mapNotNull { (key, value) ->
    value?.let { key to it }
}
    .toMap()
