package no.nav.helsearbeidsgiver.felles.utils

fun <T : Any> T.simpleName(): String =
    this::class.simpleName.orEmpty()
