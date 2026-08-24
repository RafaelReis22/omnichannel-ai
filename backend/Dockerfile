# === Build ===
FROM maven:3-eclipse-temurin-24 AS build
WORKDIR /app

# Camada de dependências separada: só invalida quando o pom muda
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# === Runtime ===
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Nunca rodar como root
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
