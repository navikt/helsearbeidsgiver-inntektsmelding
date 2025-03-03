# helsearbeidsgiver-inntektsmelding

Mono repo for team HelseArbeidsgiver

### Kjøre lokalt: 
Tjenesten består av mange mikrotjenester som kommuniserer med hverandre via rapids-and-rivers (kafka)

For å kjøre lokalt, må man starte infrastruktur via docker, og så starte respektive apper under apps
```
cd docker/local
docker-compose up #starter kafka, redis, postgres, oauth2 mock lokalt
```
Og deretter start feks: 
 LocalApiApp.kt


### Aggregert Testdekning: 
```
gradle testCodeCoverageReport
```
Output havner i build/reports/jacoco/testCodeCoverageReport

### Aggregert oversikt over avhengigheter (Software Bill Of Materials)
```
gradle allDependencies
```
