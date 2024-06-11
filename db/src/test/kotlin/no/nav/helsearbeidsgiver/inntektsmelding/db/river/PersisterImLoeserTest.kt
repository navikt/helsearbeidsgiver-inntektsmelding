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
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Innsending
import no.nav.helsearbeidsgiver.domene.inntektsmelding.deprecated.Inntektsmelding
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.EventName
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.lesOrNull
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.json.toMap
import no.nav.helsearbeidsgiver.felles.test.mock.mockInnsending
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
    private val mockInnsending = mockInnsending()

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
            repository.lagreInntektsmeldingSkjema(any(), any())
        } just Runs

        coEvery { repository.hentNyesteInntektsmelding(any()) } returns null
        coEvery { repository.hentNyesteInntektsmeldingSkjema(any()) } returns null

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.BEHOV to BehovType.PERSISTER_IM.toJson(),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
            Key.SKJEMA_INNTEKTSMELDING to mockInnsending.toJson(Innsending.serializer())
        )

        coVerify(exactly = 1) {
            repository.lagreInntektsmeldingSkjema(any(), any())
        }

        val publisert = testRapid.firstMessage().toMap()

        Key.EVENT_NAME.lesOrNull(EventName.serializer(), publisert) shouldBe EventName.INSENDING_STARTED
        Key.ER_DUPLIKAT_IM.lesOrNull(Boolean.serializer(), publisert) shouldBe false

        Key.INNTEKTSMELDING_DOKUMENT.lesOrNull(Innsending.serializer(), publisert)
            .shouldNotBeNull()
            .shouldBeEqualToIgnoringFields(mockInnsending, Inntektsmelding::tidspunkt)
    }

    @Test
    fun `ikke lagre ved duplikat`() {
        coEvery { repository.hentNyesteInntektsmelding(any()) } returns mockInntektsmelding.copy(
            tidspunkt = ZonedDateTime.now().minusHours(1).toOffsetDateTime()
        )
        coEvery { repository.hentNyesteInntektsmeldingSkjema(any()) } returns mockInnsending

        testRapid.sendJson(
            Key.EVENT_NAME to EventName.INSENDING_STARTED.toJson(),
            Key.BEHOV to BehovType.PERSISTER_IM.toJson(),
            Key.UUID to UUID.randomUUID().toJson(),
            Key.FORESPOERSEL_ID to UUID.randomUUID().toJson(),
            Key.SKJEMA_INNTEKTSMELDING to mockInnsending.copy(årsakInnsending = AarsakInnsending.ENDRING).toJson(Innsending.serializer())
        )

        coVerify(exactly = 0) {
            repository.lagreInntektsmeldingSkjema(any(), any())
        }

        val publisert = testRapid.firstMessage().toMap()

        Key.EVENT_NAME.lesOrNull(EventName.serializer(), publisert) shouldBe EventName.INSENDING_STARTED
        Key.ER_DUPLIKAT_IM.lesOrNull(Boolean.serializer(), publisert) shouldBe true
    }
}
