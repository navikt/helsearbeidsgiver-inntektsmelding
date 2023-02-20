package no.nav.helsearbeidsgiver.felles.test.mock

import io.ktor.client.statement.HttpResponse
import io.mockk.every
import no.nav.helsearbeidsgiver.felles.ForespurtData
import no.nav.helsearbeidsgiver.felles.ForslagInntekt
import no.nav.helsearbeidsgiver.felles.ForslagRefusjon
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.test.date.desember
import no.nav.helsearbeidsgiver.felles.test.date.januar
import no.nav.helsearbeidsgiver.felles.test.date.november
import no.nav.helsearbeidsgiver.felles.test.date.oktober
import no.nav.helsearbeidsgiver.felles.til
import java.util.UUID

object MockUuid {
    const val STRING = "01234567-abcd-0123-abcd-012345678901"
    val uuid: UUID = STRING.let(UUID::fromString)

    fun with(callFn: suspend () -> HttpResponse): HttpResponse =
        mockStatic(UUID::class) {
            every { UUID.randomUUID() } returns uuid

            callFn()
        }
}

fun mockForespurtDataListe(): List<ForespurtData> =
    listOf(
        ForespurtData.ArbeidsgiverPeriode(
            forslag = listOf(
                1.januar til 10.januar,
                15.januar til 20.januar
            )
        ),
        ForespurtData.Inntekt(
            forslag = ForslagInntekt(
                beregningsmåneder = listOf(
                    oktober(2017),
                    november(2017),
                    desember(2017)
                )
            )
        ),
        ForespurtData.Refusjon(forslag = emptyList())
    )

fun mockForespurtDataMedFastsattInntektListe(): List<ForespurtData> =
    listOf(
        ForespurtData.ArbeidsgiverPeriode(
            forslag = listOf(
                1.januar til 10.januar,
                15.januar til 20.januar
            )
        ),
        ForespurtData.FastsattInntekt(
            fastsattInntekt = 31415.92
        ),
        ForespurtData.Refusjon(
            forslag = listOf(
                ForslagRefusjon(
                    1.januar,
                    14.januar,
                    31415.92
                ),
                ForslagRefusjon(
                    15.januar,
                    null,
                    0.0
                )
            )
        )
    )

fun mockTrengerInntekt(): TrengerInntekt = TrengerInntekt(
    orgnr = "123",
    fnr = "456",
    sykmeldingsperioder = listOf(1.januar til 31.januar),
    forespurtData = mockForespurtDataListe()
)
