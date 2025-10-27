# MGM Module Dockerfile
FROM eclipse-temurin:21-jre

RUN useradd -u 10001 -m appuser
WORKDIR /app

COPY build/libs/*.jar /app/app.jar

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
ENV SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-default}

USER 10001
EXPOSE 8080 8085
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
