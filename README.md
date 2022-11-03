# helsearbeidsgiver-inntektsmelding

Mono repo for team HelseArbeidsgiver

### For testing

```sh
curl -vvv -H "Content-Type: application/json" -d '{
    "identitetsnummer": "10107400090",
    "orgnrUnderenhet": "874568112"
}' https://helsearbeidsgiver-im-api.dev.nav.no/api/v1/inntektsmelding

### Sonar

