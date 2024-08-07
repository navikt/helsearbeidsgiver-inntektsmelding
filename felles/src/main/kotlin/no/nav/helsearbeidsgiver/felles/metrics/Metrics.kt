package no.nav.helsearbeidsgiver.felles.metrics

import io.prometheus.client.Counter
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

    val forespoerslerBesvartFraSimba = counterMetric("forespoersler besvart fra Simba")

    val forespoerslerBesvartFraSpleis = counterMetric("forespoersler besvart fra Spleis")
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
): Summary =
    Summary
        .build()
        .name("simba_${name.toSnake()}_latency_seconds")
        .help("Latency (i sek.) for $description.")
        .labelNames("method")
        .register()

private fun counterMetric(description: String): Counter =
    Counter
        .build()
        .name("simba_${description.toSnake()}_counter")
        .help("Antall $description.")
        .register()

private fun String.toSnake(): String = replace(Regex("[ -]"), "_").lowercase()
