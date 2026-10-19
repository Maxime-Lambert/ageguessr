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
# Utilisateur systeme sans privileges : une faille d'execution de code dans la JVM ne
# donnerait pas root dans le conteneur (et rendrait une evasion vers l'hote plus dure).
RUN useradd --system --no-create-home --uid 10001 --user-group app
WORKDIR /app
COPY --from=build /src/ageguessr-api/target/*.jar app.jar
USER app

ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
