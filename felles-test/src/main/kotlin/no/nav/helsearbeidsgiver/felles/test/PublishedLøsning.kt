package no.nav.helsearbeidsgiver.felles.test

import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializerOrNull
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.test.json.JsonIgnoreUnknown
import kotlin.reflect.KClass

interface PublishedLøsning {
    val behov: List<BehovType>
    val løsning: Map<BehovType, Any>

    abstract class CompanionObj<out P : PublishedLøsning>(
        private val klass: KClass<out P>
    ) {
        abstract fun mockSuccess(): P
        abstract fun mockFailure(): P

        @OptIn(InternalSerializationApi::class)
        fun fromJson(json: JsonElement): P =
            klass.serializerOrNull()
                .shouldNotBeNull()
                .let {
                    JsonIgnoreUnknown.fromJson(it, json)
                }
    }
}
