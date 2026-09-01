package no.nav.helsearbeidsgiver.inntektsmelding.soeknad.klient

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import no.nav.hag.simba.kontrakt.domene.soeknad.Soeknad
import no.nav.helsearbeidsgiver.utils.cache.LocalCache
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.time.LocalDate

class SoeknadKlient(
    baseUrl: String,
    cacheConfig: LocalCache.Config,
    private val getAccessToken: () -> String,
) {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    private val url = "$baseUrl/api/v2/arbeidsgiver/soknader"
    private val httpClient = createHttpClient()
    private val cache = LocalCache<List<Soeknad>>(cacheConfig)

    suspend fun hentSoeknader(
        orgnr: String,
        sykmeldtFnr: String,
        eldsteFom: LocalDate,
    ): List<Soeknad> {
        "Henter søknader siden '$eldsteFom' fra Flex.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        val soeknader =
            cache.getOrPut("$orgnr|$sykmeldtFnr|$eldsteFom") {
                hentSoeknaderFraFlex(
                    orgnr = orgnr,
                    fnr = sykmeldtFnr,
                    eldsteFom = eldsteFom,
                ).mapNotNull {
                    val soeknad = tilSoeknad(it)
                    // Midlertidig: Logger for å vurdere søknader som ikke har ønskede verdier
                    if (soeknad == null) {
                        sikkerLogger.warn("Søknad uten ønskede verdier:\n${it.toJson(HentSoeknaderResponse.serializer()).toPretty()}")
                    }
                    soeknad
                }.sortedBy { it.sykmeldingsperiode.fom }
            }

        "Hentet ${soeknader.size} søknader siden '$eldsteFom' fra Flex.".also {
            logger.info(it)
            sikkerLogger.info(it)
        }

        return soeknader
    }

    private suspend fun hentSoeknaderFraFlex(
        orgnr: String,
        fnr: String,
        eldsteFom: LocalDate,
    ): List<HentSoeknaderResponse> {
        val request =
            HentSoeknaderRequest(
                orgnummer = orgnr,
                fnr = fnr,
                eldsteFom = eldsteFom,
            )

        return httpClient
            .post(url) {
                contentType(ContentType.Application.Json)
                bearerAuth(getAccessToken())
                setBody(request)
            }.body()
    }
}
