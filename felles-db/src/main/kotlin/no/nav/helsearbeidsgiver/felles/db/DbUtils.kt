package no.nav.helsearbeidsgiver.felles.db

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Query

fun <T> Query.firstOrNull(col: Column<T>): T? =
    firstOrNull()?.getOrNull(col)
