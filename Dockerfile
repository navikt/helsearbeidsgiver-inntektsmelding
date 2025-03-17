FROM gcr.io/distroless/java21
COPY build/libs/*.jar ./
ENTRYPOINT ["java", "-jar", "/app.jar"]
EXPOSE 8080
