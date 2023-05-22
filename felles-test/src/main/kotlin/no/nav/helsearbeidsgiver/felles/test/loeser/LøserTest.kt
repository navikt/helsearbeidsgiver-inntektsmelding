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
import no.nav.helsearbeidsgiver.felles.json.løsning
import no.nav.helsearbeidsgiver.felles.loeser.Løser
import no.nav.helsearbeidsgiver.felles.loeser.Løsning
import no.nav.helsearbeidsgiver.felles.test.json.fromJsonMapOnlyKeys
import no.nav.helsearbeidsgiver.felles.test.mock.mockObject
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.fromJsonMap
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import java.util.UUID

abstract class LøserTest {
    val testRapid = TestRapid()

    fun <T : Any> withTestRapid(initLøser: () -> Løser<T>): Løser<T> =
        mockObject(RapidApplication) {
            every { RapidApplication.create(any()) } returns testRapid

            initLøser()
        }

    data class LøserAnswer<out T : Any>(
        val behovType: BehovType,
        val initiateId: UUID,
        val løsning: Løsning<T>
    ) {
        companion object {
            fun <T : Any> JsonElement.toLøserAnswer(tSerializer: KSerializer<T>): LøserAnswer<T> =
                fromJsonMapOnlyKeys()
                    .let {
                        val behov = behov(it)
                        LøserAnswer(
                            behovType = behov,
                            initiateId = initiateId(it),
                            løsning = løsning(it, behov, tSerializer)
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

            private fun <T : Any> løsning(
                json: Map<Key, JsonElement>,
                behovType: BehovType,
                tSerializer: KSerializer<T>
            ): Løsning<T> =
                json[Key.LØSNING]
                    .shouldNotBeNull()
                    .fromJsonMap(BehovType.serializer())
                    .get(behovType)
                    .shouldNotBeNull()
                    .fromJson(tSerializer.løsning())
        }
    }
}
