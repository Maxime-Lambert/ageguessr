# Build context = racine du repo (pas ageguessr-api/) pour rester coherent avec les
# autres Dockerfile du projet et permettre d'ajouter des modules freres plus tard
# sans changer la convention.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src

COPY ageguessr-api/.mvn ageguessr-api/.mvn
COPY ageguessr-api/mvnw ageguessr-api/pom.xml ageguessr-api/
RUN chmod +x ageguessr-api/mvnw

WORKDIR /src/ageguessr-api
RUN ./mvnw -q -B dependency:go-offline

COPY ageguessr-api/src src
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:25-jre AS final
WORKDIR /app
COPY --from=build /src/ageguessr-api/target/*.jar app.jar

ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
