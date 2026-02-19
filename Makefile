APPNAME ?= mapstash

help: ## Display this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'

run: ## mvn spring-boot:run with local profile
	mvn spring-boot:run -Dspring-boot.run.profiles=local

clean: ## mvn clean project
	mvn clean

build: clean ## Build Spring Boot JAR with tests
	mvn package

build-skip-tests: ## Build Spring Boot JAR without tests
	mvn clean package -DskipTests

jar: build ## Alias for build target

test: ## Run unit tests
	mvn test

test-verbose: ## Run unit tests with verbose output
	mvn test -X

check-db: ## Check if db is running, used as precondition by other tasks such as bootRun
	@pg_ctl -D $(PGDATA) status; if [ $$? -eq 3 ]; then \
			echo "Starting Postgres Server with data dir $(PGDATA)"; pg_ctl -l $(PGDATA)/pg.log -D $(PGDATA) start; \
	else echo "Postgres Server already started for data dir $(PGDATA)"; fi

create-db: check-db ## Create database and user for local development
	@psql -U $(LOGNAME) -e -d postgres -c "CREATE ROLE $(APPNAME) WITH LOGIN PASSWORD '$(APPNAME)';"
	@psql -U $(LOGNAME) -e -d postgres -c "CREATE DATABASE $(APPNAME)_db OWNER $(APPNAME);"
	@psql -U $(LOGNAME) -e -d postgres -c "GRANT ALL PRIVILEGES ON DATABASE $(APPNAME)_db TO $(APPNAME);"
	@psql -U $(LOGNAME) -e -d $(APPNAME)_db -c "CREATE EXTENSION IF NOT EXISTS postgis;"
	@echo "Database $(APPNAME)_db and user $(APPNAME) created"

# THANK YOU: https://clews.id.au/posts/setting-up-postgresql-16-and-postgis-on-macos-with-homebrew/
install-postgis: check-db ## Install PostGIS extension in the database (macOS only)
	./scripts/install-postgis.sh

# read https://stevenpg.com/posts/graalvm-reflect-config-demystified/
# https://github.com/spring-projects/spring-boot/issues/42515 Document how and where to add custom GraalVM configuration file (Overwrite problem)
# java -agentlib:native-image-agent=config-merge-dir=./config -jar target/mapstash/0.0.1-SNAPSHOT.jar
# mvn -Pnative spring-boot:build-image
#mvn -Pnative spring-boot:build-image
#The issue was that having both configuration files could cause GraalVM to ignore one of them or merge them incorrectly. The unified reachability-metadata.json
#format is the correct approach for GraalVM 25.
#After rebuilding, test the native image again. If you still encounter issues with other methods, you can run the native image with the tracing agent to
#capture all reflection usage:
#java -agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image/com.mapstash/mapstash -jar target/mapstash-0.1.0-SNAPSHOT.jar
#This would generate comprehensive configuration files based on actual runtime usage
native: ## Build GraalVM native image (requires GraalVM)
	@echo "Building native image with GraalVM..."
	@echo "Note: This requires GraalVM 25 to be installed (use: sdk install java 25.0.2-graalce)"
	@echo "sdk use java 25.0.2-graalce"
	@java --version
	mvn -Pnative native:compile

native-test: ## Run tests with native image
	mvn -PnativeTest test
