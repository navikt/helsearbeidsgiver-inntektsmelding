package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.date.januar
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ArbeidsgiverPeriode
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespurtData
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.Forslag
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.Inntekt
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.Refusjon
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.TrengerForespoersel
import java.util.UUID

fun mockForespurtDataListe(): List<ForespurtData> =
    listOf(
        ArbeidsgiverPeriode(
            forslag = listOf(
                Forslag(
                    fom = 1.januar,
                    tom = 10.januar
                ),
                Forslag(
                    fom = 15.januar,
                    tom = 20.januar
                )
            )
        ),
        Refusjon,
        Inntekt
    )

fun mockTrengerForespoersel(): TrengerForespoersel =
    TrengerForespoersel(
        orgnr = "yelp-domestic-breeder",
        fnr = "relic-numerous-italicize",
        vedtaksperiodeId = UUID.randomUUID(),
        boomerang = mockBoomerang()
    )

fun mockForespoerselSvar(): ForespoerselSvar =
    ForespoerselSvar(
        orgnr = "hungry-traitor-chaplain",
        fnr = "deputize-snowy-quirk",
        vedtaksperiodeId = UUID.randomUUID(),
        fom = 1.januar,
        tom = 16.januar,
        forespurtData = mockForespurtDataListe(),
        boomerang = mockBoomerang()
    )

private fun mockBoomerang(): Map<String, JsonElement> =
    mapOf(
        Key.INITIATE_ID.str to UUID.randomUUID().toJson()
    )
