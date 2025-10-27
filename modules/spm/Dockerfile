# SPM Module Dockerfile
# Build context should be modules/spm
FROM eclipse-temurin:21-jre

# Use a non-root user for security (UID 10001)
RUN useradd -u 10001 -m appuser
WORKDIR /app

# Copy Spring Boot fat jar built by Gradle
# Expectation: gradle :modules:spm:bootJar has produced build/libs/*.jar in this context
COPY build/libs/*.jar /app/app.jar

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
ENV SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-default}

USER 10001
EXPOSE 8080

ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
