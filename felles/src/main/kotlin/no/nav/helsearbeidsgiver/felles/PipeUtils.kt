package no.nav.helsearbeidsgiver.felles

fun <T : Any> T?.orDefault(default: T) =
    this ?: default

fun Boolean.ifTrue(block: () -> Unit): Boolean =
    also { if (this) block() }

fun Boolean.ifFalse(block: () -> Unit): Boolean =
    also { if (!this) block() }
