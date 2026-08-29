# hitorro-fleet-mail-analytics — clustered profile container.
#
# Two-stage: builder does mvn + pnpm, runtime carries only the fat jar.
# Assumes hitorro parent + fleet-retrieval deps already published to the
# ~/code/hitorro-maven repo (mounted or baked in).
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /src

RUN apt-get update && apt-get install -y --no-install-recommends maven curl \
  && rm -rf /var/lib/apt/lists/*

COPY pom.xml pom.xml
COPY backend backend
COPY frontend frontend

RUN mvn -pl backend -am -B -DskipTests clean package

# --------
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /src/backend/target/hitorro-fleet-mail-analytics-*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=clustered
ENV JAVA_OPTS="-Xms256m -Xmx1g"

EXPOSE 8100
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
