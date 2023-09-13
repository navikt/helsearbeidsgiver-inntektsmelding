package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import io.mockk.coEvery
import io.mockk.mockk
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.EksternInntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.DataFelt
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.felles.utils.randomUuid
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import no.nav.helsearbeidsgiver.utils.test.date.januar


class LagreAvsenderSystemLoeserTest {

    private val rapid = TestRapid()
    private var løser: LagreAvsenderSystemLoeser
    private val repository = mockk<InntektsmeldingRepository>()

    init {
        løser = LagreAvsenderSystemLoeser(rapid, repository)
    }

    @Test
    fun `skal publisere event for AvsenderSystem Lagret`() {
        coEvery {
            repository.lagreAvsenderSystemData(any(), any())
        } returns Unit

        val avsenderSystem = EksternInntektsmelding(
            "AltinnPortal",
            "1.63",
            "AR123456",
            11.januar(2018).atStartOfDay()
        )

        rapid.sendJson(
            Key.EVENT_NAME to EventName.EKSTERN_INNTEKTSMELDING_MOTTATT.toJson(EventName.serializer()),
            Key.BEHOV to BehovType.LAGRE_AVSENDER_SYSTEM.toJson(BehovType.serializer()),
            Key.UUID to randomUuid().toJson(),
            DataFelt.EKSTERN_INNTEKTSMELDING to avsenderSystem.toJson(EksternInntektsmelding.serializer()),
            Key.FORESPOERSEL_ID to randomUuid().toJson()

        )
        val message = rapid.inspektør.message(0)
        Assertions.assertEquals(EventName.AVSENDER_SYSTEM_LAGRET.name, message.path(Key.EVENT_NAME.str).asText())
    }
}
