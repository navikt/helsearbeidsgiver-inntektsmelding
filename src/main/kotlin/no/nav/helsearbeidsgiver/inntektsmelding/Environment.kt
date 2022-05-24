package no.nav.helsearbeidsgiver.inntektsmelding

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

fun getEnv(): Environment {
    if (isLocal()) {
        val localEnvironmentPath = ".nais/local.json"
        val objectMapper = ObjectMapper().registerKotlinModule()
        return objectMapper.readValue(File(localEnvironmentPath), Environment::class.java)
    }
    return Environment(
        Dokarkiv(getEnvVar("DOKARKIV_URL"))
    )
}

data class Environment(
    val dokarkiv: Dokarkiv
)

data class Dokarkiv(
    val url: String,
)

fun isLocal(): Boolean = getEnvVar("KTOR_ENV", "local") == "local"

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Environment: Missing required variable \"$varName\"")
