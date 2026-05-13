# SonarQube + JaCoCo Guide

This project uses JaCoCo to generate test coverage reports and SonarQube to scan code quality, bugs, vulnerabilities, code smells, duplication, and test coverage.

## What Was Added

- `jacoco-maven-plugin` in every backend Maven module.
- SonarQube service in `docker-compose.yml`.
- JaCoCo XML reports are generated at:

```text
target/site/jacoco/jacoco.xml
```

inside each microservice after tests run.

## Start SonarQube

From the `stockpro` folder:

```bash
docker compose up -d sonarqube
```

Open:

```text
http://localhost:9000
```

Default login:

```text
username: admin
password: admin
```

SonarQube will ask you to set a new password on first login.

## Create A Token

In SonarQube:

```text
My Account -> Security -> Generate Tokens
```

Copy the generated token.

## Generate JaCoCo Coverage

Run this inside any microservice folder:

```bash
mvn clean test
```

Example:

```bash
cd product-service
mvn clean test
```

After this, JaCoCo creates:

```text
product-service/target/site/jacoco/jacoco.xml
```

## Scan One Microservice

Run this inside the microservice folder:

```bash
mvn sonar:sonar \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token=YOUR_TOKEN \
  -Dsonar.projectKey=stockpro-product-service \
  -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
```

Replace `YOUR_TOKEN` with your SonarQube token.

## Recommended Project Keys

Use one SonarQube project per microservice:

```text
stockpro-authservice
stockpro-api-gateway
stockpro-eureka-service
stockpro-product-service
stockpro-warehouse-service
stockpro-purchase-service
stockpro-supplier-service
stockpro-stockmovement-service
stockpro-analytics-service
stockpro-alert-service
stockpro-payment-service
```

## Example Commands

Product service:

```bash
cd product-service
mvn clean test sonar:sonar \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token=YOUR_TOKEN \
  -Dsonar.projectKey=stockpro-product-service \
  -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
```

Supplier service:

```bash
cd supplier-service
mvn clean test sonar:sonar \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token=YOUR_TOKEN \
  -Dsonar.projectKey=stockpro-supplier-service \
  -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
```

Auth service:

```bash
cd authservice
mvn clean test sonar:sonar \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token=YOUR_TOKEN \
  -Dsonar.projectKey=stockpro-authservice \
  -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
```

## Why JaCoCo Is Needed

SonarQube does not calculate Java coverage by itself. JaCoCo runs during tests and creates the coverage report. SonarQube reads that report and shows coverage in the dashboard.

Flow:

```text
JUnit/Mockito tests run
        ↓
JaCoCo measures covered lines/branches
        ↓
JaCoCo writes jacoco.xml
        ↓
SonarQube scanner reads jacoco.xml
        ↓
SonarQube dashboard shows coverage and code quality
```

## Important Notes

- SonarQube must be running before `mvn sonar:sonar`.
- `mvn test` must pass for useful coverage.
- If a service has few tests, SonarQube will show low coverage.
- Coverage is not the same as quality. SonarQube also checks bugs, security, duplications, and maintainability.
