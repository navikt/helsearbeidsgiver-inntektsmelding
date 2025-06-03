package no.nav.helsearbeidsgiver.felles.metrics

import io.ktor.http.ContentType
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import kotlinx.coroutines.runBlocking
import java.io.Writer
import kotlin.reflect.KFunction
import io.prometheus.client.Counter as PrometheusCounter
import io.prometheus.client.Summary as PrometheusSummary

object Metrics {
    val hentForespoerselEndpoint = endpointMetric("hent forespoersel")

    val hentForespoerselIdListeEndpoint = endpointMetric("hent forespoersel ID liste")

    val innsendingEndpoint = endpointMetric("innsending")

    val kvitteringEndpoint = endpointMetric("kvittering")

    val dbInntektsmelding = databaseMetric("inntektsmelding", "inntektsmelding")

    val dbSelvbestemtIm = databaseMetric("inntektsmelding", "selvbestemt_inntektsmelding")

    val aaregRequest = requestMetric("Aareg")

    val altinnRequest = requestMetric("Altinn")

    val brregRequest = requestMetric("Brreg")

    val forespoerslerBesvartFraSpleis = counterMetric("forespoersler besvart fra Spleis")

    object Expose {
        val contentType004 = ContentType.parse(TextFormat.CONTENT_TYPE_004)

        fun filteredMetricsWrite004(
            writer: Writer,
            metricNames: Set<String>,
        ) {
            TextFormat.write004(writer, CollectorRegistry.defaultRegistry.filteredMetricFamilySamples(metricNames))
        }
    }
}

class Summary internal constructor(
    private val summary: PrometheusSummary,
) {
    fun <T> recordTime(
        fnToRecord: KFunction<*>,
        block: suspend () -> T,
    ): T {
        val requestTimer: PrometheusSummary.Timer = summary.labels(fnToRecord.name).startTimer()

        return runBlocking { block() }
            .also {
                requestTimer.observeDuration()
            }
    }
}

class Counter internal constructor(
    private val counter: PrometheusCounter,
) {
    fun inc() {
        counter.inc()
    }
}

private fun endpointMetric(endpointName: String): Summary =
    latencyMetric(
        name = endpointName,
        description = "$endpointName endpoint",
    )

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
    PrometheusSummary
        .build()
        .name("simba_${name.toSnake()}_latency_seconds")
        .help("Latency (i sek.) for $description.")
        .labelNames("method")
        .register()
        .let(::Summary)

private fun counterMetric(description: String): Counter =
    PrometheusCounter
        .build()
        .name("simba_${description.toSnake()}_counter")
        .help("Antall $description.")
        .register()
        .let(::Counter)

private fun String.toSnake(): String = replace(Regex("[ -]"), "_").lowercase()
