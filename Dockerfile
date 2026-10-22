# Build frontend assets
FROM node:22-alpine AS frontend-build
WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm install
COPY frontend/ ./
RUN npm run build

# Build backend and package frontend into Spring Boot static resources
FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /workspace

COPY backend/pom.xml ./pom.xml
COPY backend/src ./src
COPY --from=frontend-build /frontend/dist ./src/main/resources/static

# Build the executable Spring Boot JAR
RUN mvn -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 appuser
COPY --from=backend-build /workspace/target/nearbuy-0.1.0.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
