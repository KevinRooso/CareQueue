# CareQueue: one image with the Spring Boot API and the React UI (same origin, no CORS).
#   docker build -t carequeue .
# Three stages; only the last one ships. Base images are pinned to a version tag and a digest:
# the tag says what it is, the digest makes the build repeatable. Bump both together.
# No secrets are baked in: database URL, passwords and GROQ_API_KEY come from the environment at run time.

# ---- 1. Build the React app (static files in /build/frontend/dist) ------------------------------
FROM node:24.22.3-alpine3.24@sha256:116b800c3754b440ee0aa3ed2f20f5bf3d99a554bfd9adeea09ea8017f6e85f5 AS frontend
WORKDIR /build/frontend
# Lockfile first: this layer (npm ci) is reused until package*.json changes.
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# ---- 2. Build the Spring Boot jar, with the React build inside it -------------------------------
FROM eclipse-temurin:25.0.5_8-jdk-alpine-3.24@sha256:c7c3d0bdf7f2bbc5a05a6712ed334d198360105634ea9adf76fca94f381b1e3f AS backend
WORKDIR /build/backend
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
# Defensive: a Windows checkout can turn mvnw's line endings into CRLF, which breaks the shebang.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
COPY backend/src/ src/
# Spring Boot serves everything in classpath:/static, so the UI ends up inside the jar.
COPY --from=frontend /build/frontend/dist/ src/main/resources/static/
# Tests run in CI (./scripts/check.sh), not here. The cache mount keeps Maven's downloads between builds.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -DskipTests package \
 && cp target/carequeue-*.jar /build/app.jar

# ---- 3. Runtime: JRE only, non-root, just the jar -----------------------------------------------
FROM eclipse-temurin:25.0.5_8-jre-alpine-3.24@sha256:e1898fd6a11fc965a994d8ace186fd3585aeb636cf8f05f47a723fcfee0b0918
LABEL org.opencontainers.image.title="CareQueue" \
      org.opencontainers.image.description="GP referral and clinic waiting-list app (synthetic data only)" \
      org.opencontainers.image.source="https://github.com/KevinRooso/CareQueue"
# Patch the one HIGH CVE an image scan found in the pinned base (CVE-2026-109908, libfontenc 1.1.9-r0,
# fixed in 1.1.10-r0); drop this line once the base image tag is bumped past it.
# Then a fixed, unprivileged user (numeric, so platforms can check it is not root).
RUN apk upgrade --no-cache libfontenc \
 && addgroup -S -g 10001 carequeue \
 && adduser -S -D -H -u 10001 -G carequeue -s /sbin/nologin carequeue
WORKDIR /app
# Owned by root and read-only for the app user: the process cannot rewrite its own code.
COPY --from=backend /build/app.jar /app/app.jar
USER 10001:10001
# Sized for small instances (Render free = 512 MB, 0.1 CPU): heap up to 75% of the container's memory
# limit, the low-overhead single-threaded GC, exit (so the platform restarts us) on OutOfMemoryError,
# and only the quick C1 JIT compiler: on 0.1 CPU it halves startup (measured 155 s -> 72 s) for a
# lower peak speed this I/O-bound app does not notice. Drop TieredStopAtLevel=1 on a bigger instance.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -XX:TieredStopAtLevel=1"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
