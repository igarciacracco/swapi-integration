# ---- build ----------------------------------------------------------------
# Built on JDK 21, the version the artifact targets.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# Dependencies first, so this layer stays cached until the POM changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests run in CI; images are built from commits that already passed.
RUN ./mvnw -B -q clean package -DskipTests

# ---- runtime --------------------------------------------------------------
# Alpine JRE: no compiler, and about half the size of the Ubuntu-based image.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root user, created before the COPY so the app cannot rewrite its own jar.
RUN addgroup -S app && adduser -S -G app -H -s /sbin/nologin app
COPY --from=build /src/target/*.jar app.jar
USER app

# Heap sized from the container limit; SerialGC suits the 0.1 CPU free tier.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD ["sh", "-c", "wget -qO- http://localhost:${PORT:-8080}/actuator/health || exit 1"]

# sh expands JAVA_OPTS; exec keeps java as PID 1 so it gets SIGTERM and shuts down cleanly.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
