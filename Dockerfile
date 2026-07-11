# Paso 1: Compilar la aplicación usando Maven
FROM maven:3.8.8-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Paso 2: Crear la imagen final ligera para ejecutar la aplicación
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Exponer el puerto genérico (Render asignará el real internamente a través de $PORT)
EXPOSE 8080

# Comando para arrancar el microservicio
ENTRYPOINT ["java", "-jar", "app.jar"]