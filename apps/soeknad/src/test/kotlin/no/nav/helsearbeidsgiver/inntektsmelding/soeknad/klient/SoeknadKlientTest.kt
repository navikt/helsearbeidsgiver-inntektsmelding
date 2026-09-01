package no.nav.helsearbeidsgiver.inntektsmelding.soeknad.klient

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.testCoroutineScheduler
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import no.nav.hag.simba.kontrakt.domene.soeknad.Soeknad
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.til
import no.nav.helsearbeidsgiver.domene.inntektsmelding.v1.utils.tilDager
import no.nav.helsearbeidsgiver.utils.json.serializer.list
import no.nav.helsearbeidsgiver.utils.json.toJson
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.juli
import no.nav.helsearbeidsgiver.utils.test.date.juni
import no.nav.helsearbeidsgiver.utils.test.date.mai
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class SoeknadKlientTest :
    FunSpec({

        test("henter søknader") {
            val klient = mockSoeknadKlient(HttpStatusCode.OK to Mock.okResponseJson)

            val soeknader =
                klient.hentSoeknader(
                    orgnr = Orgnr.genererGyldig().verdi,
                    sykmeldtFnr = Fnr.genererGyldig().verdi,
                    eldsteFom = 1.april,
                )

            soeknader shouldContainExactly
                listOf(
                    Mock.soeknadArbeidstaker,
                    Mock.soeknadArbeidstakerGradert,
                    Mock.soeknadArbeidstakerGradertFraArbeid,
                    Mock.soeknadBehandlingsdager,
                )
        }

        test("feiler ved 4xx-feil") {
            val klient = mockSoeknadKlient(HttpStatusCode.NotFound to "")

            val e =
                shouldThrowExactly<ClientRequestException> {
                    klient.hentSoeknader(
                        orgnr = Orgnr.genererGyldig().verdi,
                        sykmeldtFnr = Fnr.genererGyldig().verdi,
                        eldsteFom = 13.mai,
                    )
                }

            e.response.status shouldBe HttpStatusCode.NotFound
        }

        context("med retries") {
            // Unngår venting på delay-funksjonen
            coroutineTestScope = true

            test("lykkes ved færre 5xx-feil enn maks retries (3)") {
                val klient =
                    mockSoeknadKlient(
                        HttpStatusCode.InternalServerError to "",
                        HttpStatusCode.InternalServerError to "",
                        HttpStatusCode.InternalServerError to "",
                        HttpStatusCode.OK to Mock.okResponseJson,
                    )

                shouldNotThrowAny {
                    klient.hentSoeknader(
                        orgnr = Orgnr.genererGyldig().verdi,
                        sykmeldtFnr = Fnr.genererGyldig().verdi,
                        eldsteFom = 13.mai,
                    )
                }
            }

            test("feiler ved flere 5xx-feil enn maks retries (3)") {
                val klient =
                    mockSoeknadKlient(
                        HttpStatusCode.InternalServerError to "",
                        HttpStatusCode.InternalServerError to "",
                        HttpStatusCode.InternalServerError to "",
                        HttpStatusCode.InternalServerError to "",
                    )

                val e =
                    shouldThrowExactly<ServerResponseException> {
                        klient.hentSoeknader(
                            orgnr = Orgnr.genererGyldig().verdi,
                            sykmeldtFnr = Fnr.genererGyldig().verdi,
                            eldsteFom = 13.mai,
                        )
                    }

                e.response.status shouldBe HttpStatusCode.InternalServerError
            }

            test("kall feiler og prøver på nytt ved timeout") {
                val klient =
                    mockSoeknadKlient(
                        HttpStatusCode.OK to "timeout",
                        HttpStatusCode.OK to "timeout",
                        HttpStatusCode.OK to "timeout",
                        HttpStatusCode.OK to Mock.okResponseJson,
                        scheduler = testCoroutineScheduler,
                    )

                shouldNotThrowAny {
                    klient.hentSoeknader(
                        orgnr = Orgnr.genererGyldig().verdi,
                        sykmeldtFnr = Fnr.genererGyldig().verdi,
                        eldsteFom = 13.mai,
                    )
                }
            }
        }
    })

private object Mock {
    val soeknadArbeidstaker =
        Soeknad.Arbeidstaker(
            soeknadId = UUID.randomUUID(),
            vedtaksperiodeId = UUID.randomUUID(),
            sykmeldingsperiode = 5.mars til 24.mars,
            egenmeldingerFraSykmelding = listOf(3.mars til 4.mars),
            erGradert = false,
        )
    val soeknadArbeidstakerGradert =
        Soeknad.Arbeidstaker(
            soeknadId = UUID.randomUUID(),
            vedtaksperiodeId = UUID.randomUUID(),
            sykmeldingsperiode = 1.april til 21.april,
            egenmeldingerFraSykmelding = emptyList(),
            erGradert = true,
        )
    val soeknadArbeidstakerGradertFraArbeid =
        Soeknad.Arbeidstaker(
            soeknadId = UUID.randomUUID(),
            vedtaksperiodeId = UUID.randomUUID(),
            sykmeldingsperiode = 7.mai til 28.mai,
            egenmeldingerFraSykmelding = listOf(6.mai til 6.mai),
            erGradert = true,
        )
    val soeknadBehandlingsdager =
        Soeknad.Behandlingsdager(
            soeknadId = UUID.randomUUID(),
            sykmeldingsperiode = 3.juli til 21.juli,
            behandlingsdager =
                setOf(
                    4.juli,
                    11.juli,
                    18.juli,
                ),
        )
    val sykmeldingsgrad100 =
        HentSoeknaderResponse.Sykmeldingsgrad(
            grad = 100,
            faktiskGrad = null,
        )

    val okResponseJson =
        listOf(
            // vanlig arbeidstakersøknad
            soeknadArbeidstaker.let {
                HentSoeknaderResponse(
                    sykepengesoknadUuid = it.soeknadId.toString(),
                    vedtaksperiodeId = it.vedtaksperiodeId.toString(),
                    fom = it.sykmeldingsperiode.fom,
                    tom = it.sykmeldingsperiode.tom,
                    soknadstype = HentSoeknaderResponse.Soeknadstype.ARBEIDSTAKERE,
                    soknadsperioder = listOf(sykmeldingsgrad100),
                    egenmeldingsdagerFraSykmelding = it.egenmeldingerFraSykmelding.tilDager().sorted(),
                    behandlingsdager = emptyList(),
                )
            },
            // vanlig arbeidstakersøknad, gradert
            soeknadArbeidstakerGradert.let {
                HentSoeknaderResponse(
                    sykepengesoknadUuid = it.soeknadId.toString(),
                    vedtaksperiodeId = it.vedtaksperiodeId.toString(),
                    fom = it.sykmeldingsperiode.fom,
                    tom = it.sykmeldingsperiode.tom,
                    soknadstype = HentSoeknaderResponse.Soeknadstype.ARBEIDSTAKERE,
                    soknadsperioder =
                        listOf(
                            sykmeldingsgrad100,
                            HentSoeknaderResponse.Sykmeldingsgrad(
                                grad = 60,
                                faktiskGrad = null,
                            ),
                            sykmeldingsgrad100,
                        ),
                    egenmeldingsdagerFraSykmelding = it.egenmeldingerFraSykmelding.tilDager().sorted(),
                    behandlingsdager = emptyList(),
                )
            },
            // vanlig arbeidstakersøknad, gradert fra arbeid
            soeknadArbeidstakerGradertFraArbeid.let {
                HentSoeknaderResponse(
                    sykepengesoknadUuid = it.soeknadId.toString(),
                    vedtaksperiodeId = it.vedtaksperiodeId.toString(),
                    fom = it.sykmeldingsperiode.fom,
                    tom = it.sykmeldingsperiode.tom,
                    soknadstype = HentSoeknaderResponse.Soeknadstype.ARBEIDSTAKERE,
                    soknadsperioder =
                        listOf(
                            sykmeldingsgrad100,
                            HentSoeknaderResponse.Sykmeldingsgrad(
                                grad = 100,
                                faktiskGrad = 30,
                            ),
                            sykmeldingsgrad100,
                        ),
                    egenmeldingsdagerFraSykmelding = it.egenmeldingerFraSykmelding.tilDager().sorted(),
                    behandlingsdager = emptyList(),
                )
            },
            // arbeidstakersøknad uten vedtaksperiode-ID
            HentSoeknaderResponse(
                sykepengesoknadUuid = UUID.randomUUID().toString(),
                vedtaksperiodeId = null,
                fom = 12.juni,
                tom = 24.juni,
                soknadstype = HentSoeknaderResponse.Soeknadstype.ARBEIDSTAKERE,
                soknadsperioder = listOf(sykmeldingsgrad100),
                egenmeldingsdagerFraSykmelding = listOf(11.juni),
                behandlingsdager = emptyList(),
            ),
            // vanlig behandlingsdagersøknad
            soeknadBehandlingsdager.let {
                HentSoeknaderResponse(
                    sykepengesoknadUuid = it.soeknadId.toString(),
                    vedtaksperiodeId = null,
                    fom = it.sykmeldingsperiode.fom,
                    tom = it.sykmeldingsperiode.tom,
                    soknadstype = HentSoeknaderResponse.Soeknadstype.BEHANDLINGSDAGER,
                    soknadsperioder = listOf(sykmeldingsgrad100),
                    egenmeldingsdagerFraSykmelding = listOf(2.juli),
                    behandlingsdager = it.behandlingsdager.toList(),
                )
            },
            // behandlingsdagersøknad uten behandlingsdager
            HentSoeknaderResponse(
                sykepengesoknadUuid = UUID.randomUUID().toString(),
                vedtaksperiodeId = null,
                fom = 5.august,
                tom = 31.august,
                soknadstype = HentSoeknaderResponse.Soeknadstype.BEHANDLINGSDAGER,
                soknadsperioder = listOf(sykmeldingsgrad100),
                egenmeldingsdagerFraSykmelding = listOf(4.august),
                behandlingsdager = emptyList(),
            ),
        ).toJson(HentSoeknaderResponse.serializer().list())
            .toString()
}
