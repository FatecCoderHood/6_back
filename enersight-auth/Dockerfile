# --- Build stage ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Copy only the POM first so dependency resolution is cached independently of source changes
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build /build/target/enersight-auth-*.jar app.jar
USER app

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
