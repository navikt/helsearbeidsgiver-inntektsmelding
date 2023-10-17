package no.nav.helsearbeidsgiver.felles.utils

import java.util.UUID

/** Brukes for å kunne mocke UUID-en i tester. */
fun randomUuid(): UUID =
    UUID.randomUUID()
