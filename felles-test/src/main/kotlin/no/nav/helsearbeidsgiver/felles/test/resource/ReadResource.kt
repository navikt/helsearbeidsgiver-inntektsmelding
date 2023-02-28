package no.nav.helsearbeidsgiver.felles.test.resource

fun String.readResource(): String =
    ClassLoader.getSystemResource(this)?.readText()!!
