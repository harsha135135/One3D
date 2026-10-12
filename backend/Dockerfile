# Build stage: compile and package the backend with the Maven wrapper.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package \
    && cp target/one3d-backend-*.jar /src/app.jar

# Runtime stage: JRE only, non-root user. Configuration comes from the
# environment (see compose.yaml); no credentials are baked into the image.
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --no-create-home one3d
COPY --from=build /src/app.jar /app/backend.jar
USER one3d
# Inside the container the API listens on all interfaces; only the reverse
# proxy (and, locally, nothing else) can reach it on the internal network.
ENV SERVER_ADDRESS=0.0.0.0 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=6 \
    CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080"]
ENTRYPOINT ["java", "-jar", "/app/backend.jar"]
