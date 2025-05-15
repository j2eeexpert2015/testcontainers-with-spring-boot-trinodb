# Dockerfile
FROM maven:3.8.6-eclipse-temurin-17-alpine as build
WORKDIR /workspace/app

# Copy pom.xml and source code
COPY pom.xml .
COPY src ./src

# Build with Maven
RUN mvn package -DskipTests

# Create the final image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]