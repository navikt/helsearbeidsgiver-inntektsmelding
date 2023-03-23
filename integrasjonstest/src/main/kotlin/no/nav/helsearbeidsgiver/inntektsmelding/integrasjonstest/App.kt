package no.nav.helsearbeidsgiver.inntektsmelding.integrasjonstest

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.inntekt.InntektKlient
import no.nav.helsearbeidsgiver.inntektsmelding.aareg.createAareg
import no.nav.helsearbeidsgiver.inntektsmelding.akkumulator.createAkkumulator
import no.nav.helsearbeidsgiver.inntektsmelding.brreg.createBrreg
import no.nav.helsearbeidsgiver.inntektsmelding.db.Database
import no.nav.helsearbeidsgiver.inntektsmelding.db.Repository
import no.nav.helsearbeidsgiver.inntektsmelding.db.createDb
import no.nav.helsearbeidsgiver.inntektsmelding.forespoerselmottatt.createForespoerselMottatt
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.RedisStore
import no.nav.helsearbeidsgiver.inntektsmelding.innsending.createInnsending
import no.nav.helsearbeidsgiver.inntektsmelding.inntekt.createInntekt
import no.nav.helsearbeidsgiver.inntektsmelding.joark.createJoark
import no.nav.helsearbeidsgiver.inntektsmelding.notifikasjon.createNotifikasjon
import no.nav.helsearbeidsgiver.inntektsmelding.pdl.createPdl
import no.nav.helsearbeidsgiver.inntektsmelding.preutfylt.createPreutfylt
import no.nav.helsearbeidsgiver.pdl.PdlClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val sikkerLogger: Logger = LoggerFactory.getLogger("tjenestekall")
internal val logger: Logger = LoggerFactory.getLogger("helsearbeidsgiver-im-integrasjon")

fun main() {
    sikkerLogger.info("Starter Integrasjon app...") // dummy placeholder fordi man må ha en App i modulen
    RapidApplication
        .create(System.getenv())
        // .buildApp()
        .start()
}

fun RapidsConnection.buildApp(
    redisStore: RedisStore,
    database: Database,
    repository: Repository,
    aaregClient: AaregClient,
    brregClient: BrregClient,
    inntektKlient: InntektKlient,
    dokarkivClient: DokArkivClient,
    pdlClient: PdlClient,
    arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient,
    notifikasjonLink: String
): RapidsConnection {
    sikkerLogger.info("Starting App")
    this.createAareg(aaregClient)
    this.createAkkumulator(redisStore)
    this.createBrreg(brregClient, true)
    this.createInnsending(redisStore)
    this.createDb(database, repository)
    // this.createDb(database, repository)
    // this.createDistribusjon()
    this.createForespoerselMottatt()
    // this.createHelsebro()
    this.createInntekt(inntektKlient)
    this.createJoark(dokarkivClient)
    this.createPdl(pdlClient)
    this.createPreutfylt()
    this.createNotifikasjon(arbeidsgiverNotifikasjonKlient, notifikasjonLink)
    return this
}
