#!/usr/bin/env bash
set -euo pipefail

SONAR_HOST_URL="${SONAR_HOST_URL:-http://localhost:9000}"
JAVA17_HOME="${JAVA17_HOME:-/usr/local/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home}"

if [[ -z "${SONAR_TOKEN:-}" ]]; then
  echo "Missing SONAR_TOKEN."
  echo "Run: export SONAR_TOKEN='your-token-here'"
  exit 1
fi

SONAR_TOKEN="$(printf '%s' "$SONAR_TOKEN" | xargs)"

if [[ ! -d "$JAVA17_HOME" ]]; then
  echo "Java 17 not found at: $JAVA17_HOME"
  echo "Set JAVA17_HOME to your Java 17 home and run again."
  exit 1
fi

services=(
  "authservice:stockpro-authservice"
  "api-gateway:stockpro-api-gateway"
  "eureka-service:stockpro-eureka-service"
  "product-service:stockpro-product-service"
  "warehouse-service:stockpro-warehouse-service"
  "purchase-service:stockpro-purchase-service"
  "supplier-service:stockpro-supplier-service"
  "stockmovement-services:stockpro-stockmovement-service"
  "analytics-service:stockpro-analytics-service"
  "alert-service:stockpro-alert-service"
  "payment-service:stockpro-payment-service"
)

for entry in "${services[@]}"; do
  service="${entry%%:*}"
  project_key="${entry##*:}"

  echo
  echo "========================================"
  echo "Running SonarQube scan: $service"
  echo "Project key: $project_key"
  echo "========================================"

  (
    cd "$service"
    env JAVA_HOME="$JAVA17_HOME" mvn clean test sonar:sonar \
      -Dsonar.host.url="$SONAR_HOST_URL" \
      -Dsonar.token="$SONAR_TOKEN" \
      -Dsonar.projectKey="$project_key" \
      -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
  )
done

echo
echo "All SonarQube scans completed."
