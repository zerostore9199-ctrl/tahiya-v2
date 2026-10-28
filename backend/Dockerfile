# ---- Build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# ---- Run ----
FROM eclipse-temurin:17-jre-jammy
# DejaVu Sans carries the Arabic glyphs used in PDF reports.
RUN apt-get update && apt-get install -y --no-install-recommends fonts-dejavu-core curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10002 app
WORKDIR /app
COPY --from=build /src/target/attendance-backend-*.jar app.jar
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC" \
    PDF_FONT_PATH=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s CMD curl -fs http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
