package no.nav.hag.simba.utils.felles.test.mock

import kotlin.random.Random

fun randomDigitString(length: Int): String =
    List(length) { Random.nextInt(10) }
        .joinToString(separator = "")
