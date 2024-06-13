package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.coEvery
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Inntekt
import no.nav.helsearbeidsgiver.felles.InntektPerMaaned
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.juli
import no.nav.helsearbeidsgiver.utils.test.date.juni
import no.nav.helsearbeidsgiver.utils.test.date.mai
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InntektSelvbestemtIT : EndToEndTest() {

    @Test
    fun `skal hente inntekt for selvbestemt inntektsmelding`() {
        coEvery { inntektClient.hentInntektPerOrgnrOgMaaned(any(), any(), any(), any(), any()) } returns Mock.inntektPerOrgnrOgMaaned

        publish(
            Key.EVENT_NAME to EventName.INNTEKT_SELVBESTEMT_REQUESTED.toJson(),
            Key.UUID to Mock.transaksjonId.toJson(),
            Key.DATA to "".toJson(),
            Key.FNR to Mock.fnr.toJson(Fnr.serializer()),
            Key.ORGNRUNDERENHET to Mock.orgnr.toJson(Orgnr.serializer()),
            Key.SKJAERINGSTIDSPUNKT to Mock.inntektsdato.toJson()
        )

        messages.filter(BehovType.INNTEKT)
            .firstAsMap()
            .shouldContainExactly(
                mapOf(
                    Key.EVENT_NAME to EventName.INNTEKT_SELVBESTEMT_REQUESTED.toJson(),
                    Key.BEHOV to BehovType.INNTEKT.toJson(),
                    Key.UUID to Mock.transaksjonId.toJson(),
                    Key.ORGNRUNDERENHET to Mock.orgnr.toJson(Orgnr.serializer()),
                    Key.FNR to Mock.fnr.toJson(Fnr.serializer()),
                    Key.SKJAERINGSTIDSPUNKT to Mock.inntektsdato.toJson(LocalDateSerializer)
                )
            )

        messages.filter(Key.INNTEKT)
            .firstAsMap()
            .shouldContainExactly(
                mapOf(
                    Key.EVENT_NAME to EventName.INNTEKT_SELVBESTEMT_REQUESTED.toJson(),
                    Key.UUID to Mock.transaksjonId.toJson(),
                    Key.DATA to "".toJson(),
                    Key.INNTEKT to Mock.inntektPerMaaned.toJson(Inntekt.serializer())
                )
            )
    }

    private object Mock {
        val fnr = Fnr.genererGyldig()
        val orgnr = Orgnr.genererGyldig()
        val inntektsdato = 15.juli(2019)
        val transaksjonId: UUID = UUID.randomUUID()

        val inntektPerOrgnrOgMaaned = mapOf(
            orgnr.verdi to
                mapOf(
                    april(2019) to 40000.0,
                    mai(2019) to 42000.0,
                    juni(2019) to 44000.0
                )
        )

        val inntektPerMaaned = Inntekt(
            inntektPerOrgnrOgMaaned[orgnr.verdi]
                .shouldNotBeNull()
                .map { InntektPerMaaned(it.key, it.value) }
        )
    }
}
