# helsearbeidsgiver-inntektsmelding

Mono repo for team HelseArbeidsgiver

### For testing

```sh
curl -X POST -d '{
    "identitetsnummer": "03053726622",
    "orgnrUnderenhet": "874568112"
}' https://helsearbeidsgiver-im-api.dev.nav.no/api/v1/inntektsmelding

