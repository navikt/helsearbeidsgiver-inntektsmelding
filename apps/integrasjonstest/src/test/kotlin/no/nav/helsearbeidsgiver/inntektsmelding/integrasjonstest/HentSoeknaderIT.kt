package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerifySequence
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import no.nav.hag.simba.kontrakt.domene.bro.forespoersel.test.mockForespoerselFraBro
import no.nav.hag.simba.kontrakt.domene.forespoersel.Forespoersel
import no.nav.hag.simba.kontrakt.domene.soeknad.Soeknad
import no.nav.hag.simba.kontrakt.domene.soeknad.test.mockSoeknadArbeidstaker
import no.nav.hag.simba.kontrakt.domene.soeknad.test.mockSoeknadBehandlingsdager
import no.nav.hag.simba.kontrakt.resultat.soeknad.hentSoeknaderResultatSerializer
import no.nav.hag.simba.utils.felles.EventName
import no.nav.hag.simba.utils.felles.Key
import no.nav.hag.simba.utils.felles.Tekst
import no.nav.hag.simba.utils.felles.json.toJson
import no.nav.hag.simba.utils.valkey.RedisPrefix
import no.nav.hag.simba.utils.valkey.ResultJson
import no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest.utils.EndToEndTest
import no.nav.helsearbeidsgiver.utils.json.fromJson
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.util.UUID
import kotlin.text.get

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HentSoeknaderIT : EndToEndTest() {
    @Test
    fun `henter søknader _uten_ behandlingsdager`() {
        val kontekstId: UUID = UUID.randomUUID()
        val orgnr = Orgnr.genererGyldig()
        val sykmeldtFnr = Fnr.genererGyldig()
        val erBehandlingsdager = false
        val eldsteFom = LocalDate.now().minusYears(3)

        val forespoersel1 = mockForespoerselFraBro()
        val forespoersel2 = mockForespoerselFraBro()
        val soeknadUtenForespoersel1 = mockSoeknadArbeidstaker()
        val soeknadUtenForespoersel2 = mockSoeknadArbeidstaker()
        val soeknader =
            listOf(
                mockSoeknadArbeidstaker().copy(vedtaksperiodeId = forespoersel1.vedtaksperiodeId),
                soeknadUtenForespoersel1,
                soeknadUtenForespoersel2,
                mockSoeknadBehandlingsdager(),
                mockSoeknadArbeidstaker().copy(vedtaksperiodeId = forespoersel2.vedtaksperiodeId),
                mockSoeknadBehandlingsdager(),
            )

        coEvery { soeknadKlient.hentSoeknader(orgnr.verdi, sykmeldtFnr.verdi, eldsteFom) } returns soeknader

        mockForespoerselSvarFraHelsebro(
            listOf(forespoersel1, forespoersel2),
        )

        publish(*requestEvent(kontekstId, orgnr, sykmeldtFnr, erBehandlingsdager))

        coVerifySequence {
            soeknadKlient.hentSoeknader(orgnr.verdi, sykmeldtFnr.verdi, eldsteFom)
        }

        readSuccess(kontekstId).also {
            it.first shouldContainExactly
                listOf(
                    forespoersel1.forespoerselId to forespoersel1.toForespoersel(),
                    forespoersel2.forespoerselId to forespoersel2.toForespoersel(),
                )
            it.second shouldContainExactly listOf(soeknadUtenForespoersel1, soeknadUtenForespoersel2)
            it.third.shouldBeEmpty()
        }
    }

    @Test
    fun `henter søknader _med_ behandlingsdager`() {
        val kontekstId: UUID = UUID.randomUUID()
        val orgnr = Orgnr.genererGyldig()
        val sykmeldtFnr = Fnr.genererGyldig()
        val erBehandlingsdager = true
        val eldsteFom = LocalDate.now().minusYears(3)

        val soeknadBehandlingsdager1 = mockSoeknadBehandlingsdager()
        val soeknadBehandlingsdager2 = mockSoeknadBehandlingsdager()
        val soeknader =
            listOf(
                mockSoeknadArbeidstaker(),
                mockSoeknadArbeidstaker(),
                soeknadBehandlingsdager1,
                mockSoeknadArbeidstaker(),
                soeknadBehandlingsdager2,
            )

        coEvery { soeknadKlient.hentSoeknader(orgnr.verdi, sykmeldtFnr.verdi, eldsteFom) } returns soeknader

        publish(*requestEvent(kontekstId, orgnr, sykmeldtFnr, erBehandlingsdager))

        coVerifySequence {
            soeknadKlient.hentSoeknader(orgnr.verdi, sykmeldtFnr.verdi, eldsteFom)
        }

        readSuccess(kontekstId).also {
            it.first.shouldBeEmpty()
            it.second.shouldBeEmpty()
            it.third shouldContainExactly listOf(soeknadBehandlingsdager1, soeknadBehandlingsdager2)
        }
    }

    @Test
    fun `ingen søknader funnet`() {
        val kontekstId: UUID = UUID.randomUUID()
        val orgnr = Orgnr.genererGyldig()
        val sykmeldtFnr = Fnr.genererGyldig()
        val erBehandlingsdager = false
        val eldsteFom = LocalDate.now().minusYears(3)

        coEvery { soeknadKlient.hentSoeknader(orgnr.verdi, sykmeldtFnr.verdi, eldsteFom) } returns emptyList()

        mockForespoerselSvarFraHelsebro(emptyList())

        publish(*requestEvent(kontekstId, orgnr, sykmeldtFnr, erBehandlingsdager))

        coVerifySequence {
            soeknadKlient.hentSoeknader(orgnr.verdi, sykmeldtFnr.verdi, eldsteFom)
        }

        readSuccess(kontekstId).also {
            it.first.shouldBeEmpty()
            it.second.shouldBeEmpty()
            it.third.shouldBeEmpty()
        }
    }

    @Test
    fun `svarer med med feil dersom noe går galt`() {
        val kontekstId: UUID = UUID.randomUUID()
        val orgnr = Orgnr.genererGyldig()
        val sykmeldtFnr = Fnr.genererGyldig()
        val erBehandlingsdager = false
        val eldsteFom = LocalDate.now().minusYears(3)

        coEvery { soeknadKlient.hentSoeknader(orgnr.verdi, sykmeldtFnr.verdi, eldsteFom) } throws NullPointerException()

        publish(*requestEvent(kontekstId, orgnr, sykmeldtFnr, erBehandlingsdager))

        coVerifySequence {
            soeknadKlient.hentSoeknader(orgnr.verdi, sykmeldtFnr.verdi, eldsteFom)
        }

        readFailure(kontekstId) shouldBe Tekst.TEKNISK_FEIL_FORBIGAAENDE
    }

    private fun readSuccess(kontekstId: UUID): Triple<List<Pair<UUID, Forespoersel>>, List<Soeknad.Arbeidstaker>, List<Soeknad.Behandlingsdager>> {
        val resultJson = readResult(kontekstId)

        resultJson.failure.shouldBeNull()

        return resultJson.success.shouldNotBeNull().fromJson(hentSoeknaderResultatSerializer)
    }

    private fun readFailure(kontekstId: UUID): String {
        val resultJson = readResult(kontekstId)

        resultJson.success.shouldBeNull()

        return resultJson.failure.shouldNotBeNull().fromJson(String.serializer())
    }

    private fun readResult(kontekstId: UUID): ResultJson =
        redisConnection
            .get(RedisPrefix.HentSoeknader, kontekstId)
            ?.fromJson(ResultJson.serializer())
            .shouldNotBeNull()
}

private fun requestEvent(
    kontekstId: UUID,
    orgnr: Orgnr,
    sykmeldtFnr: Fnr,
    erBehandlingsdager: Boolean,
): Array<Pair<Key, JsonElement>> =
    arrayOf(
        Key.EVENT_NAME to EventName.REQUEST_HENT_SOEKNAD_LISTE.toJson(),
        Key.KONTEKST_ID to kontekstId.toJson(UuidSerializer),
        Key.DATA to
            mapOf(
                Key.ORGNR_UNDERENHET to orgnr.toJson(),
                Key.SYKMELDT_FNR to sykmeldtFnr.toJson(),
                Key.ER_BEHANDLINGSDAGER to erBehandlingsdager.toJson(Boolean.serializer()),
            ).toJson(),
    )
