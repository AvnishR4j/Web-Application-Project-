# Build the Spring Boot jar, then run it on a small Java 21 runtime image.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/booking-*.jar app.jar
# Free hosting tiers have about 512 MB of memory and a fraction of a CPU. TieredStopAtLevel=1 uses only the
# quick JIT compiler, which makes start-up and the first requests much faster on a small CPU.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
EXPOSE 8080
# Hosts such as Render pass the port to listen on in PORT.
CMD ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]
