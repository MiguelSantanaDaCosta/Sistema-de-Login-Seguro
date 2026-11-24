# ---------- Build ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -q -DskipTests package && cp target/*.jar app.jar

# ---------- Runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --no-create-home thindesk
COPY --from=build /app/app.jar app.jar
USER thindesk
ENV SERVER_PORT=8000 JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8000
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar app.jar"]
