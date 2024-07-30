package no.nav.helsearbeidsgiver.inntektsmelding.db.river

import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.serialization.builtins.serializer
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.AarsakInnsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.mock.mockInntektsmelding
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.firstMessage
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import no.nav.helsearbeidsgiver.inntektsmelding.db.InntektsmeldingRepository
import no.nav.helsearbeidsgiver.utils.json.toJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import java.util.UUID

class PersisterImLoeserTest {
    private val testRapid = TestRapid()
    private val repository = mockk<InntektsmeldingRepository>()

    private val mockInntektsmelding = mockInntektsmelding()

    init {
        PersisterImLoeser(testRapid, repository)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
        clearAllMocks()
    }

    @Test
    fun `skal publisere event for Inntektsmelding Mottatt`() {
        coEvery {
            repository.lagreInntektsmelding(any(), any())
        } just Runs

        coEvery { repository.hentNyesteInntektsmelding(any()) } returns null

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_SKJEMA_LAGRET.toJson(),
            Key.BEHOV to BehovType.PERSISTER_IM.toJson(),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
            Key.INNTEKTSMELDING to mockInntektsmelding.toJson(Inntektsmelding.serializer()),
        )

        coVerify(exactly = 1) {
            repository.lagreInntektsmelding(any(), any())
        }

        val publisert = testRapid.firstMessage().toMap()

        Key.EVENT_NAME.lesOrNull(EventName.serializer(), publisert) shouldBe EventName.INNTEKTSMELDING_SKJEMA_LAGRET
        Key.ER_DUPLIKAT_IM.lesOrNull(Boolean.serializer(), publisert) shouldBe false

        Key.INNTEKTSMELDING_DOKUMENT
            .lesOrNull(Inntektsmelding.serializer(), publisert)
            .shouldNotBeNull()
            .shouldBeEqualToIgnoringFields(mockInntektsmelding, Inntektsmelding::tidspunkt)
    }

    @Test
    fun `ikke lagre ved duplikat`() {
        coEvery { repository.hentNyesteInntektsmelding(any()) } returns mockInntektsmelding.copy(
            tidspunkt = ZonedDateTime.now().minusHours(1).toOffsetDateTime()
        )

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INNTEKTSMELDING_SKJEMA_LAGRET.toJson(),
            Key.BEHOV to BehovType.PERSISTER_IM.toJson(),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
            Key.INNTEKTSMELDING to mockInntektsmelding.copy(årsakInnsending = AarsakInnsending.ENDRING).toJson(Inntektsmelding.serializer()),
        )

        coVerify(exactly = 0) {
            repository.lagreInntektsmelding(any(), any())
        }

        val publisert = testRapid.firstMessage().toMap()

        Key.EVENT_NAME.lesOrNull(EventName.serializer(), publisert) shouldBe EventName.INNTEKTSMELDING_SKJEMA_LAGRET
        Key.ER_DUPLIKAT_IM.lesOrNull(Boolean.serializer(), publisert) shouldBe true
    }
}
