package no.nav.helsearbeidsgiver.felles.test.loeser

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.every
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.loesning
import no.nav.helsearbeidsgiver.felles.loeser.Loeser
import no.nav.helsearbeidsgiver.felles.loeser.Løsning
import no.nav.helsearbeidsgiver.felles.test.json.fromJsonMapOnlyKeys
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.fromJsonMap
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.test.mock.mockObject
import java.util.UUID

abstract class LoeserTest {
    val testRapid = TestRapid()

    fun <T : Any> withTestRapid(initLoeser: () -> Loeser<T>): Loeser<T> =
        mockObject(RapidApplication) {
            every { RapidApplication.create(any()) } returns testRapid

            initLoeser()
        }

    data class LoeserAnswer<out T : Any>(
        val behovType: BehovType,
        val initiateId: UUID,
        val loesning: Løsning<T>
    ) {
        companion object {
            fun <T : Any> JsonElement.toLøserAnswer(tSerializer: KSerializer<T>): LoeserAnswer<T> =
                fromJsonMapOnlyKeys()
                    .let {
                        val behov = behov(it)
                        LoeserAnswer(
                            behovType = behov,
                            initiateId = initiateId(it),
                            loesning = loesning(it, behov, tSerializer)
                        )
                    }

            private fun behov(json: Map<Key, JsonElement>): BehovType =
                json[Key.BEHOV]
                    .shouldNotBeNull()
                    .fromJson(BehovType.serializer().list())
                    .shouldNotBeEmpty()
                    .first()

            private fun initiateId(json: Map<Key, JsonElement>): UUID =
                json[Key.INITIATE_ID]
                    .shouldNotBeNull()
                    .fromJson(UuidSerializer)

            private fun <T : Any> loesning(
                json: Map<Key, JsonElement>,
                behovType: BehovType,
                tSerializer: KSerializer<T>
            ): Løsning<T> =
                json[Key.LØSNING]
                    .shouldNotBeNull()
                    .fromJsonMap(BehovType.serializer())
                    .get(behovType)
                    .shouldNotBeNull()
                    .fromJson(tSerializer.loesning())
        }
    }
}
