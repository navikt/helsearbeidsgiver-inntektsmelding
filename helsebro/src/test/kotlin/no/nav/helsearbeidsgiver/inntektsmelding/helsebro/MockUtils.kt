package no.nav.helsearbeidsgiver.inntektsmelding.helsebro

import kotlinx.serialization.json.JsonElement
import no.nav.helsearbeidsgiver.felles.Key
import no.nav.helsearbeidsgiver.felles.json.toJson
import no.nav.helsearbeidsgiver.felles.test.date.januar
import no.nav.helsearbeidsgiver.felles.test.mock.mockForespurtDataListe
import no.nav.helsearbeidsgiver.felles.til
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.ForespoerselSvar
import no.nav.helsearbeidsgiver.inntektsmelding.helsebro.domene.TrengerForespoersel
import java.util.UUID

fun mockTrengerForespoersel(): TrengerForespoersel =
    TrengerForespoersel(
        forespoerselId = UUID.randomUUID(),
        boomerang = mockBoomerang()
    )

fun mockForespoerselSvarMedSuksess(): ForespoerselSvar = ForespoerselSvar(
    forespoerselId = UUID.randomUUID(),
    resultat = mockForespoerselSvarSuksess(),
    feil = null,
    boomerang = mockBoomerang()
)

fun mockForespoerselSvarMedFeil(): ForespoerselSvar = ForespoerselSvar(
    forespoerselId = UUID.randomUUID(),
    resultat = null,
    feil = ForespoerselSvar.Feil.FORESPOERSEL_IKKE_FUNNET,
    boomerang = mockBoomerang()
)

fun mockForespoerselSvarSuksess(): ForespoerselSvar.Suksess =
    ForespoerselSvar.Suksess(
        orgnr = "hungry-traitor-chaplain",
        fnr = "deputize-snowy-quirk",
        sykmeldingsperioder = listOf(1.januar til 16.januar),
        forespurtData = mockForespurtDataListe()
    )

private fun mockBoomerang(): Map<String, JsonElement> =
    mapOf(
        Key.INITIATE_ID.str to UUID.randomUUID().toJson()
    )
