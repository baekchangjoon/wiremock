#!/bin/bash
set -e

echo "Starting WireMock via Gradle with JaCoCo agent..."
# Use standard Gradle task runWireMock which blocks.
# Put it in background.
./gradlew runWireMock > wiremock.log 2>&1 &
PID=$!
echo "Gradle WireMock task started with PID $PID"

# Wait for WireMock to start
echo "Waiting for WireMock to be ready..."
for i in {1..60}; do
  if curl -s http://localhost:8080/__admin > /dev/null; then
    echo "WireMock is up!"
    break
  fi
  sleep 1
done

echo "Running E2E tests..."
python3 e2e_test.py

echo "Stopping WireMock..."
# Killing gradle wrapper script might not kill the java process immediately, but should propagate
kill $PID
wait $PID 2>/dev/null || true

# Wait a bit for coverage data to flush
sleep 5

echo "Generating Coverage Report..."
# Move execution data to where jacocoTestReport expects it
mkdir -p wiremock-core/build/jacoco
cp e2e.exec wiremock-core/build/jacoco/test.exec

# Run report
./gradlew :wiremock-core:jacocoTestReport -x test

echo "Report generated."
