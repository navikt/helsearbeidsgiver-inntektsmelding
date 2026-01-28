package no.nav.hag.simba.utils.db.exposed

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.jdbc.Query

fun <T> Query.firstOrNull(col: Column<T>): T? = firstOrNull()?.getOrNull(col)
