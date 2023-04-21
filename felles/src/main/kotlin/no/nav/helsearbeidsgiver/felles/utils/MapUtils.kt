package no.nav.helsearbeidsgiver.felles.utils

fun <K : Any, V : Any, R : Any> Map<K, V>.mapKeysNotNull(transform: (K) -> R?) =
    mapNotNull { (key, value) ->
        val newKey = transform(key)
        if (newKey == null) {
            null
        } else {
            newKey to value
        }
    }
        .toMap()

fun <K : Any, V : Any, R : Any> Map<K, V>.mapValuesNotNull(transform: (V) -> R?) =
    mapNotNull { (key, value) ->
        val newValue = transform(value)
        if (newValue == null) {
            null
        } else {
            key to newValue
        }
    }
        .toMap()
