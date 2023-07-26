package no.nav.helsearbeidsgiver.felles.test.mock

import io.ktor.client.statement.HttpResponse
import io.mockk.every
import no.nav.helsearbeidsgiver.felles.ForespoerselStatus
import no.nav.helsearbeidsgiver.felles.ForespoerselType
import no.nav.helsearbeidsgiver.felles.ForespurtData
import no.nav.helsearbeidsgiver.felles.ForslagInntekt
import no.nav.helsearbeidsgiver.felles.ForslagRefusjon
import no.nav.helsearbeidsgiver.felles.TrengerInntekt
import no.nav.helsearbeidsgiver.felles.til
import no.nav.helsearbeidsgiver.utils.test.date.desember
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.november
import no.nav.helsearbeidsgiver.utils.test.date.oktober
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
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

fun mockForespurtData(): ForespurtData =
    ForespurtData(
        arbeidsgiverperiode = ForespurtData.Arbeidsgiverperiode(
            paakrevd = true
        ),
        inntekt = ForespurtData.Inntekt(
            paakrevd = true,
            forslag = ForslagInntekt.Grunnlag(
                beregningsmaaneder = listOf(
                    oktober(2017),
                    november(2017),
                    desember(2017)
                )
            )
        ),
        refusjon = ForespurtData.Refusjon(
            paakrevd = true,
            forslag = ForslagRefusjon(
                perioder = listOf(
                    ForslagRefusjon.Periode(
                        fom = 10.januar(2017),
                        beloep = 10.48
                    ),
                    ForslagRefusjon.Periode(
                        fom = 2.februar(2017),
                        beloep = 98.26
                    )
                ),
                opphoersdato = 26.februar(2017)
            )
        )
    )

fun mockForespurtDataMedFastsattInntekt(): ForespurtData =
    ForespurtData(
        arbeidsgiverperiode = ForespurtData.Arbeidsgiverperiode(
            paakrevd = true
        ),
        inntekt = ForespurtData.Inntekt(
            paakrevd = false,
            forslag = ForslagInntekt.Fastsatt(
                fastsattInntekt = 31415.92
            )
        ),
        refusjon = ForespurtData.Refusjon(
            paakrevd = true,
            forslag = ForslagRefusjon(
                perioder = listOf(
                    ForslagRefusjon.Periode(
                        fom = 1.januar,
                        beloep = 31415.92
                    ),
                    ForslagRefusjon.Periode(
                        fom = 15.januar,
                        beloep = 3.14
                    )
                ),
                opphoersdato = null
            )
        )
    )

fun mockTrengerInntekt(): TrengerInntekt =
    TrengerInntekt(
        type = ForespoerselType.KOMPLETT,
        status = ForespoerselStatus.AKTIV,
        orgnr = "123",
        fnr = "456",
        skjaeringstidspunkt = 11.januar(2018),
        sykmeldingsperioder = listOf(2.januar til 31.januar),
        egenmeldingsperioder = listOf(1.januar til 1.januar),
        forespurtData = mockForespurtData()
    )
