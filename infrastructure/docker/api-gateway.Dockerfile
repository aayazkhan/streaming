FROM gradle:8.13-jdk17 AS build
WORKDIR /workspace
COPY . .
RUN gradle :backend:api-gateway:installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/backend/api-gateway/build/install/api-gateway/ ./
USER 10001
EXPOSE 8080
ENTRYPOINT ["./bin/api-gateway"]
