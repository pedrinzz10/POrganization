# syntax=docker/dockerfile:1

# ---------- build: compila o jar com o Maven wrapper ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Dependências primeiro: mudar só o código não baixa tudo de novo
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp -q dependency:go-offline

COPY src src
RUN ./mvnw -B -ntp -q package -DskipTests \
    && java -Djarmode=tools -jar target/porganization-*.jar extract --layers --launcher --destination extracted

# ---------- runtime: só a JRE, sem o código-fonte nem o Maven ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S -G app -H app

# Camadas do Spring Boot: dependências mudam pouco e ficam em cache
COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./

USER app

# O Render define PORT; application.yml usa server.port=${PORT:8080}
EXPOSE 8080

# Plano gratuito do Render tem 512 MB: a heap usa até 75% da memória do container
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "org.springframework.boot.loader.launch.JarLauncher"]
