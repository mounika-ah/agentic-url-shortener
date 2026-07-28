FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

COPY src src

RUN ./mvnw clean package -DskipTests -B


FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd --system --create-home appuser

COPY --from=builder \
    /workspace/target/agentic-url-shortener-0.0.1-SNAPSHOT.jar \
    app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]