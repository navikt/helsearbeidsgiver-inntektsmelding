package no.nav.hag.simba.utils.valkey

import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.domene.ResultJson
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.helsearbeidsgiver.utils.collection.mapKeysNotNull
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

class RedisStore(
    private val redis: RedisConnection,
    private val keyPrefix: RedisPrefix,
) {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    fun lesResultat(kontekstId: UUID): ResultJson? =
        resultatKey(kontekstId)
            .les()
            ?.fromJson(ResultJson.serializer())

    fun lesAlleMellomlagrede(kontekstId: UUID): Map<Key, JsonElement> {
        val keys = Key.entries.map { mellomlagringKey(kontekstId, it) }
        val prefix = listOf(keyPrefix.name, kontekstId.toString()).joinKeySeparator(withPostfix = true)

        return redis
            .getAll(keys)
            .mapKeysNotNull { parseKey(it, prefix) }
            .mapValuesNotNull(::parseValue)
    }

    fun skrivResultat(
        kontekstId: UUID,
        value: ResultJson,
    ) {
        resultatKey(kontekstId).skriv(value.toJson())
    }

    fun skrivMellomlagring(
        kontekstId: UUID,
        key: Key,
        value: JsonElement,
    ) {
        mellomlagringKey(kontekstId, key).skriv(value)
    }

    private fun String.les(): JsonElement? =
        redis
            .get(this)
            ?.parseJson()

    private fun String.skriv(value: JsonElement) {
        sikkerLogger.debug("Skriver til redis:\n$this -> ${value.toPretty()}")
        redis.set(this, value.toString())
    }

    private fun resultatKey(kontekstId: UUID): String = listOf(keyPrefix.name, kontekstId.toString()).joinKeySeparator()

    private fun mellomlagringKey(
        kontekstId: UUID,
        key: Key,
    ): String =
        listOf(
            resultatKey(kontekstId),
            key.toString(),
        ).joinKeySeparator()

    private fun parseKey(
        key: String,
        prefix: String,
    ): Key? =
        key
            .removePrefix(prefix)
            .runCatching(Key::fromString)
            .getOrElse { error ->
                "Feil med nøkkel '$key' i Redis.".also {
                    logger.error(it)
                    sikkerLogger.error(it, error)
                }
                null
            }

    private fun parseValue(value: String): JsonElement? =
        value
            .runCatching(String::parseJson)
            .getOrElse { error ->
                "Klarte ikke parse redis-verdi.".also {
                    logger.error(it)
                    sikkerLogger.error("$it\nvalue=$value", error)
                }
                null
            }
}

private fun List<String>.joinKeySeparator(withPostfix: Boolean = false): String {
    val keyPartSeparator = "#"
    return joinToString(
        separator = keyPartSeparator,
        postfix = if (withPostfix) keyPartSeparator else "",
    )
}

enum class RedisPrefix {
    AktiveOrgnr,
    ApiInnsending,
    HentForespoersel,
    HentForespoerslerForVedtaksperiodeIdListe,
    HentSelvbestemtIm,
    Innsending,
    InntektSelvbestemt,
    Inntekt,
    Kvittering,
    LagreSelvbestemtIm,
    TilgangForespoersel,
    TilgangOrg,
}
