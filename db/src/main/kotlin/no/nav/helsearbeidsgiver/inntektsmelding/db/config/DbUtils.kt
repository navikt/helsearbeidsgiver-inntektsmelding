package no.nav.helsearbeidsgiver.inntektsmelding.db.config

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Query

fun <T> Query.firstOrNull(col: Column<T>): T? =
    firstOrNull()?.getOrNull(col)
