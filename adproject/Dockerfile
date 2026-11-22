FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Dependencies first, in their own layer: it only changes when pom.xml does, so with the build cache a code-only change
# skips the download. It is an optimisation only: if it cannot resolve something, the package step below still fetches it.
#
# When Maven Central fails a download once (a transient 5xx), Maven writes a "*.lastUpdated" marker and refuses to retry
# that artifact for a day; the cached layer would then replay the failure on every later build ("POM ... is missing").
# So downloads are retried here, and any failure markers are deleted before the layer is cached.
ENV MAVEN_OPTS="-Dmaven.wagon.http.retryHandler.count=5 -Dmaven.wagon.http.retryHandler.requestSentEnabled=true"
COPY pom.xml .
RUN (mvn -B -ntp dependency:go-offline || true) \
    && find /root/.m2 -name '*.lastUpdated' -delete

COPY src ./src
# The tests have already run (CI gates the build on them), so they are neither run nor compiled here.
# -U makes Maven re-check anything an earlier attempt recorded as missing instead of trusting that record.
RUN mvn -B -ntp -U -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

ENV SPRING_PROFILES_ACTIVE=prod
ENV APP_UPLOAD_SUBMISSIONS_DIR=/app/uploads/submissions
ENV JAVA_OPTS=""

# The commit this image was built from; shown on the monitoring page and /actuator/info, so a deploy (or a rollback)
# can be verified by asking the running app which version it is.
ARG APP_REVISION=unknown
ENV APP_REVISION=${APP_REVISION}

RUN useradd --create-home --shell /usr/sbin/nologin appuser \
    && mkdir -p /app/logs /app/uploads/submissions \
    && chown -R appuser:appuser /app

COPY --from=build /workspace/target/*.jar /app/app.jar

USER appuser

EXPOSE 8080

# CVE-2026-114938 ("Java4Shell"): heap corruption in the AVX-512 Base64 decoder intrinsic, reachable before login through
# the remember-me cookie. -XX:-UseBASE64Intrinsics turns that intrinsic off. It sits here, not in JAVA_OPTS, so a
# JAVA_OPTS set in Dokploy cannot drop it. Remove it once the base image is on Temurin 21.0.13.1 or newer.
ENTRYPOINT ["sh", "-c", "java -XX:-UseBASE64Intrinsics $JAVA_OPTS -jar /app/app.jar"]
