package no.nav.helsearbeidsgiver.felles.metrics

import io.prometheus.client.Summary
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KFunction

object Metrics {
    val dbSelvbestemtIm = databaseMetric("inntektsmelding", "selvbestemt_inntektsmelding")

    val dbSelvbestemtSak = databaseMetric("notifikasjon", "selvbestemt_sak")

    val aaregRequest = requestMetric("Aareg")

    val agNotifikasjonRequest = requestMetric("AG-notifikasjon")

    val altinnRequest = requestMetric("Altinn")

    val brregRequest = requestMetric("Brreg")

    val dokArkivRequest = requestMetric("DokArkiv")

    val inntektRequest = requestMetric("Inntekt")

    val pdlRequest = requestMetric("PDL")
}

fun <T> Summary.recordTime(
    fnToRecord: KFunction<*>,
    block: suspend () -> T,
): T {
    val requestTimer: Summary.Timer = labels(fnToRecord.name).startTimer()

    return runBlocking { block() }
        .also {
            requestTimer.observeDuration()
        }
}

private fun databaseMetric(
    dbName: String,
    tableName: String,
): Summary =
    latencyMetric(
        name = "db_${dbName}_$tableName",
        description = "database '$dbName' and table '$tableName'",
    )

private fun requestMetric(clientName: String): Summary =
    latencyMetric(
        name = "client_$clientName",
        description = "$clientName-request",
    )

private fun latencyMetric(
    name: String,
    description: String,
): Summary {
    val nameInSnake = name.replace(Regex("[ -]"), "_").lowercase()
    return Summary
        .build()
        .name("simba_${nameInSnake}_latency_seconds")
        .help("Latency (i sek.) for $description.")
        .labelNames("method")
        .register()
}
