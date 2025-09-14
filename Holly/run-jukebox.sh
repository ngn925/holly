#!/bin/bash

# Jukebox API Startup Script
# Compiles the project, starts the Jetty service, and displays endpoint info and API docs location.

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Project directory
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo -e "${GREEN}=== Jukebox API Startup Script ===${NC}"
echo "Project Directory: $PROJECT_DIR"

# Step 1: Check prerequisites
echo -e "\n${YELLOW}Step 1: Checking prerequisites...${NC}"

# Check for Java
if ! command -v java &> /dev/null; then
    echo -e "${RED}Error: Java is not installed. Please install Java 17+.${NC}"
    exit 1
fi
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo -e "${RED}Error: Java 17+ is required. Found version $JAVA_VERSION.${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Java OK (version >= 17)${NC}"

# Check for Gradle
if ! command -v ./gradlew &> /dev/null; then
    echo -e "${RED}Error: Gradle wrapper (gradlew) not found. Run 'gradle wrapper' or install Gradle.${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Gradle OK${NC}"

# Step 2: Compile the project
echo -e "\n${YELLOW}Step 2: Compiling the project...${NC}"
./gradlew clean build --no-daemon || {
    echo -e "${RED}Error: Compilation failed. Check logs above.${NC}"
    exit 1
}
echo -e "${GREEN}✓ Compilation successful${NC}"

# Step 3: Start Jetty service
echo -e "\n${YELLOW}Step 3: Starting Jetty service...${NC}"
echo -e "${YELLOW}The application will start on http://localhost:8080${NC}"

# Remove old log file
rm -f jukebox.log

# Start bootRun with nohup to keep it running
nohup ./gradlew bootRun --no-daemon > jukebox.log 2>&1 &
PID=$!

# Wait for the application to start (check for "Started JukeboxApplication" in logs)
echo -e "${YELLOW}Waiting for application to start...${NC}"
for i in {1..30}; do
    if grep -q "Started JukeboxApplication in" jukebox.log; then
        echo -e "${GREEN}✓ Jetty service started (PID: $PID)${NC}"
        break
    fi
    if [ $i -eq 30 ]; then
        echo -e "${RED}Error: Application failed to start within 30 seconds. Check jukebox.log for errors.${NC}"
        tail -20 jukebox.log
        kill $PID 2>/dev/null
        exit 1
    fi
    sleep 1
done

# Step 4: Display endpoint info and API docs
echo -e "\n${GREEN}=== Jukebox API Ready! ===${NC}"
echo -e "${YELLOW}API Documentation:${NC}"
echo "  Swagger UI: http://localhost:8080/swagger-ui.html (OpenAPI docs)"
echo "  OpenAPI JSON: http://localhost:8080/v3/api-docs"

echo -e "\n${YELLOW}Available Endpoints:${NC}"
echo -e "${GREEN}1. GET /api/artist/mbid?artistName={name}${NC}"
echo "   Description: Retrieves the MusicBrainz ID (MBID) and name for an artist."
echo "   Example: curl 'http://localhost:8080/api/artist/mbid?artistName=ABBA'"
echo "   Response: {\"name\":\"ABBA\",\"mbid\":\"d87e52c5-bb8d-4da8-b941-9f4928627dc8\"}"

echo -e "${GREEN}2. GET /api/artist/details?mbid={mbid}${NC}"
echo "   Description: Retrieves detailed artist info, including description and albums."
echo "   Example: curl 'http://localhost:8080/api/artist/details?mbid=d87e52c5-bb8d-4da8-b941-9f4928627dc8'"
echo "   Response: {\"name\":\"ABBA\",\"description\":\"<p>ABBA is...</p>\",\"mbid\":\"...\",\"albums\":[...]}"

echo -e "${GREEN}3. GET /api/artist/discography?artistName={name}${NC}"
echo "   Description: Retrieves full artist discography (combines MBID lookup and details)."
echo "   Example: curl 'http://localhost:8080/api/artist/discography?artistName=Electric%20Light%20Orchestra'"
echo "   Response: Same as /details, but takes artist name."

echo -e "\n${YELLOW}Shutdown Instructions:${NC}"
echo "  To stop the server, press Ctrl+C in this terminal."
echo "  Alternatively, find the process ID ($PID) and run: kill $PID"

echo -e "\n${GREEN}Application is running. Logs are in jukebox.log.${NC}"

# Trap Ctrl+C to clean up
trap 'echo -e "\n${RED}Stopping application...${NC}"; kill $PID 2>/dev/null; echo -e "${RED}Application stopped.${NC}"; exit 0' INT TERM

# Wait for the process to complete
wait $PID