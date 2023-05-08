# helsearbeidsgiver-inntektsmelding

[![Quality gate](https://sonarcloud.io/api/project_badges/quality_gate?project=navikt_helsearbeidsgiver-inntektsmelding)](https://sonarcloud.io/summary/new_code?id=navikt_helsearbeidsgiver-inntektsmelding)


Mono repo for team HelseArbeidsgiver 

### For testing

```sh
curl -vvv -H "Content-Type: application/json" -d '{
    "identitetsnummer": "10107400090",
    "orgnrUnderenhet": "874568112"
}' https://helsearbeidsgiver-im-api.intern.dev.nav.no/api/v1/inntektsmelding
```
### Lokal utvikling: 

```
cd docker/local
docker-compose up #starter kafka, redis, postgre, oauth2 mock lokalt

Start:
 LocalIntegrasjonApp.kt
 LocalAkkumulatorApp.kt
 LocalApiApp.kt
```

### Aggregert Testdekning: 
```
gradle testCodeCoverageReport
```
Output havner i build/reports/jacoco/testCodeCoverageReport