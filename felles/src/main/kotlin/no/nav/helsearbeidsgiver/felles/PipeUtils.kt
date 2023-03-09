package no.nav.helsearbeidsgiver.felles

fun <T : Any> T?.orDefault(default: T) =
    this ?: default

fun Boolean.ifTrue(block: () -> Unit): Boolean =
    also { if (this) block() }

fun Boolean.ifFalse(block: () -> Unit): Boolean =
    also { if (!this) block() }

fun <A, B, X> Pair<A, B>.mapFirst(mapFn: (A) -> X): Pair<X, B> =
    Pair(
        first = mapFn(first),
        second = second
    )

fun <A, B, Y> Pair<A, B>.mapSecond(mapFn: (B) -> Y): Pair<A, Y> =
    Pair(
        first = first,
        second = mapFn(second)
    )
