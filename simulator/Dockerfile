# Build stage: compile and package the simulator with the Maven wrapper.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package

# Runtime stage: JRE only, non-root user, journal on a volume.
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --no-create-home simulator \
    && mkdir /data && chown simulator /data
COPY --from=build /src/target/one3d-simulator.jar /app/simulator.jar
USER simulator
# Inside the container the simulator listens on all interfaces; whether the
# port is reachable from outside is decided by how the container is published.
ENV SIMULATOR_BIND=0.0.0.0 \
    SIMULATOR_PORT=8090 \
    SIMULATOR_JOURNAL=/data/journal.db \
    SIMULATOR_DEV_CONTROLS=false
EXPOSE 8090
VOLUME /data
HEALTHCHECK --interval=5s --timeout=2s --retries=10 \
    CMD ["java", "-cp", "/app/simulator.jar", "store.one3d.simulator.HealthCheck"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", "-jar", "/app/simulator.jar"]
