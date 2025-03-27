package no.nav.helsearbeidsgiver.felles.rapidsrivers.redis

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.ResultJson
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.utils.collection.mapKeysNotNull
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

private const val KEY_PART_SEPARATOR = "#"
private const val KEY_FEIL_POSTFIX = "feil"

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
        val prefix = listOf(keyPrefix.name, kontekstId.toString()).joinKeySeparator(withPostfix = true)

        return Key.entries
            .map { mellomlagringKey(kontekstId, it) }
            .lesAlle { removePrefix(prefix) }
    }

    fun lesAlleFeil(kontekstId: UUID): Map<Key, String> {
        val prefix = listOf(keyPrefix.name, kontekstId.toString()).joinKeySeparator(withPostfix = true)
        val postfix = KEY_PART_SEPARATOR + KEY_FEIL_POSTFIX

        return Key.entries
            .map { feilKey(kontekstId, it) }
            .lesAlle {
                removePrefix(prefix)
                    .removeSuffix(postfix)
            }.mapValues { it.value.fromJson(String.serializer()) }
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

    fun skrivFeil(
        kontekstId: UUID,
        key: Key,
        value: String,
    ) {
        feilKey(kontekstId, key).skriv(value.toJson())
    }

    private fun String.les(): JsonElement? =
        redis
            .get(this)
            ?.parseJson()
            .also { value ->
                sikkerLogger.debug("Leser fra redis:\n$this -> ${value?.toPretty()}")
            }

    private fun List<String>.lesAlle(redisKeyToJsonKey: String.() -> String): Map<Key, JsonElement> =
        redis
            .getAll(this)
            .mapKeysNotNull { key ->
                key
                    .redisKeyToJsonKey()
                    .runCatching(Key::fromString)
                    .getOrElse { error ->
                        "Feil med nøkkel '$key' i Redis.".also {
                            logger.error(it)
                            sikkerLogger.error(it, error)
                        }
                        null
                    }
            }.mapValuesNotNull { value ->
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

    private fun feilKey(
        kontekstId: UUID,
        key: Key,
    ): String =
        listOf(
            mellomlagringKey(kontekstId, key),
            KEY_FEIL_POSTFIX,
        ).joinKeySeparator()
}

private fun List<String>.joinKeySeparator(withPostfix: Boolean = false): String =
    joinToString(
        separator = KEY_PART_SEPARATOR,
        postfix = if (withPostfix) KEY_PART_SEPARATOR else "",
    )

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
