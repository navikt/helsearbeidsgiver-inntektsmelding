package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.coEvery
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.domene.Inntekt
import no.nav.helsearbeidsgiver.felles.domene.InntektPerMaaned
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.rapidsrivers.KafkaKey
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
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
            Key.KONTEKST_ID to Mock.transaksjonId.toJson(),
            Key.DATA to
                mapOf(
                    Key.FNR to Mock.fnr.toJson(),
                    Key.ORGNR_UNDERENHET to Mock.orgnr.toJson(),
                    Key.INNTEKTSDATO to Mock.inntektsdato.toJson(),
                ).toJson(),
        )

        messages
            .filter(BehovType.HENT_INNTEKT)
            .firstAsMap()
            .shouldContainExactly(
                mapOf(
                    Key.EVENT_NAME to EventName.INNTEKT_SELVBESTEMT_REQUESTED.toJson(),
                    Key.BEHOV to BehovType.HENT_INNTEKT.toJson(),
                    Key.KONTEKST_ID to Mock.transaksjonId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.SVAR_KAFKA_KEY to KafkaKey(Mock.fnr).toJson(),
                            Key.ORGNR_UNDERENHET to Mock.orgnr.toJson(),
                            Key.FNR to Mock.fnr.toJson(),
                            Key.INNTEKTSDATO to Mock.inntektsdato.toJson(),
                        ).toJson(),
                ),
            )

        messages
            .filter(Key.INNTEKT)
            .firstAsMap()
            .shouldContainExactly(
                mapOf(
                    Key.EVENT_NAME to EventName.INNTEKT_SELVBESTEMT_REQUESTED.toJson(),
                    Key.KONTEKST_ID to Mock.transaksjonId.toJson(),
                    Key.DATA to
                        mapOf(
                            Key.SVAR_KAFKA_KEY to KafkaKey(Mock.fnr).toJson(),
                            Key.ORGNR_UNDERENHET to Mock.orgnr.toJson(),
                            Key.FNR to Mock.fnr.toJson(),
                            Key.INNTEKTSDATO to Mock.inntektsdato.toJson(),
                            Key.INNTEKT to Mock.inntektPerMaaned.toJson(Inntekt.serializer()),
                        ).toJson(),
                ),
            )
    }

    private object Mock {
        val fnr = Fnr.genererGyldig()
        val orgnr = Orgnr.genererGyldig()
        val inntektsdato = 15.juli(2019)
        val transaksjonId: UUID = UUID.randomUUID()

        val inntektPerOrgnrOgMaaned =
            mapOf(
                orgnr.verdi to
                    mapOf(
                        april(2019) to 40000.0,
                        mai(2019) to 42000.0,
                        juni(2019) to 44000.0,
                    ),
            )

        val inntektPerMaaned =
            Inntekt(
                inntektPerOrgnrOgMaaned[orgnr.verdi]
                    .shouldNotBeNull()
                    .map { InntektPerMaaned(it.key, it.value) },
            )
    }
}
