package no.nav.helsearbeidsgiver.inntektsmelding.db

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.JsonElement
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.helsearbeidsgiver.felles.BehovType
import no.nav.helsearbeidsgiver.felles.HentPersistertLøsning
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.fromJson
import no.nav.helsearbeidsgiver.felles.json.toJsonElement
import no.nav.helsearbeidsgiver.felles.test.rapidsrivers.sendJson
import org.junit.jupiter.api.Test

internal class PersisterImLøserTest {

    private val rapid = TestRapid()
    private var løser: PersisterImLøser
    private val repository = mockk<Repository>()

    init {
        løser = PersisterImLøser(rapid, repository)
    }

    private fun sendMelding(vararg melding: Pair<Key, JsonElement>): HentPersistertLøsning {
        rapid.reset()
        rapid.sendJson(*melding.toList().toTypedArray())
        return rapid.inspektør.message(0).path(Key.LØSNING.str).get(BehovType.HENT_PERSISTERT_IM.name).toJsonElement().fromJson()
    }

    @Test
    fun `skal publisere event for Inntektsmelding Mottatt`() {
        coEvery {
            repository.lagre(any(), any())
        } returns "abc"
//        val løsning = sendMelding(
//            Key.BEHOV to listOf(BehovType.PERSISTER_IM.name).toJson(String::toJson),
//            Key.ID to UUID.randomUUID().toJson(),
//            Key.UUID to "uuid".toJson(),
//            Key.INNTEKTSMELDING to InnsendingRequest(
//                "",
//                "",
//                emptyList(),
//                emptyList(),
//                emptyList(),
//                LocalDate.now(),
//                emptyList(),
//                Inntekt(
//                    bekreftet = true,
//                    500.0,
//                    InntektEndringÅrsak.Bonus,
//                    true
//                ),
//                FullLønnIArbeidsgiverPerioden(
//                    true
//                ),
//                Refusjon(
//                    true
//                ),
//                emptyList(),
//                ÅrsakInnsending.Ny,
//                true
//            ).toJson()
//        )
//        assertEquals("", løsning)
    }
}
