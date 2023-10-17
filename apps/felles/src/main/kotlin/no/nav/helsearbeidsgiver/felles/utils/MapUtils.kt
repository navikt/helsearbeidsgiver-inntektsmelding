package no.nav.helsearbeidsgiver.felles.utils

fun <K : Any, V : Any> mapOfNotNull(vararg pair: Pair<K, V?>): Map<K, V> = mapOf(*pair).mapNotNull { (key, value) ->
    value?.let { key to it }
}
    .toMap()
