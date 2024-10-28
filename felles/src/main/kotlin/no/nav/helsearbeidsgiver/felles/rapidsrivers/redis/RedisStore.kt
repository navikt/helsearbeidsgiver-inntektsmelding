package no.nav.helsearbeidsgiver.felles.rapidsrivers.redis

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.utils.collection.mapKeysNotNull
import no.nav.helsearbeidsgiver.utils.collection.mapValuesNotNull
import no.nav.helsearbeidsgiver.utils.json.parseJson
import no.nav.helsearbeidsgiver.utils.json.toPretty
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger
import java.util.UUID

private const val KEY_PART_SEPARATOR = "#"
private const val KEY_FEIL_POSTFIX = "Feilmelding"

class RedisStore(
// class RedisStore<Success : Any, Failure : Any>(
    private val redis: RedisConnection,
    private val keyPrefix: RedisPrefix,
//    private val successSerializer: KSerializer<Success>,
//    private val failureSerializer: KSerializer<Failure>,
) {
    private val logger = logger()
    private val sikkerLogger = sikkerLogger()

    fun lesAlleMellomlagrede(transaksjonId: UUID): Map<Key, JsonElement> {
        val prefix = listOf(keyPrefix.name, transaksjonId.toString()).joinKeySeparator(withPostfix = true)

        val keys = Key.entries.map { mellomlagringKey(transaksjonId, it) }

        return redis
            .getAll(keys)
            .mapKeysNotNull { key ->
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
    }

    fun lesResultat(transaksjonId: UUID): JsonElement? = resultatKey(transaksjonId).les()

    fun lesFeil(transaksjonId: UUID): JsonElement? = feilKey(transaksjonId).les()

    fun skrivMellomlagring(
        transaksjonId: UUID,
        key: Key,
        value: JsonElement,
    ) {
        mellomlagringKey(transaksjonId, key).skriv(value)
    }

    fun skrivResultat(
        transaksjonId: UUID,
        value: JsonElement,
    ) {
        resultatKey(transaksjonId).skriv(value)
    }

    // TODO Skriv feil for hver key separat
    fun skrivFeil(
        transaksjonId: UUID,
        value: JsonElement,
    ) {
        feilKey(transaksjonId).skriv(value)
    }

    private fun String.les(): JsonElement? =
        redis
            .get(this)
            ?.parseJson()
            .also { value ->
                sikkerLogger.debug("Leser fra redis:\n$this -> ${value?.toPretty()}")
            }

    private fun String.skriv(value: JsonElement) {
        sikkerLogger.debug("Skriver til redis:\n$this -> ${value.toPretty()}")
        redis.set(this, value.toString())
    }

    private fun mellomlagringKey(
        transaksjonId: UUID,
        key: Key,
    ): String = listOf(keyPrefix.name, transaksjonId.toString(), key.toString()).joinKeySeparator()

    private fun resultatKey(transaksjonId: UUID): String = listOf(keyPrefix.name, transaksjonId.toString()).joinKeySeparator()

    private fun feilKey(transaksjonId: UUID): String = listOf(keyPrefix.name, transaksjonId.toString(), KEY_FEIL_POSTFIX).joinKeySeparator()
}

private fun List<String>.joinKeySeparator(withPostfix: Boolean = false): String =
    joinToString(
        separator = KEY_PART_SEPARATOR,
        postfix = if (withPostfix) KEY_PART_SEPARATOR else "",
    )

enum class RedisPrefix {
    AktiveOrgnr,
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
