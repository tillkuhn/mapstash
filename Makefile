APPNAME ?= mapstash

help: ## Display this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'

run: ## mvn spring-boot:run with local profile
	mvn spring-boot:run -Dspring-boot.run.profiles=local

build: ## Build Spring Boot JAR with tests
	mvn clean package

build-skip-tests: ## Build Spring Boot JAR without tests
	mvn clean package -DskipTests

jar: build ## Alias for build target

check-db: ## Check if db is running, used as precondition by other tasks such as bootRun
	@pg_ctl -D $(PGDATA) status; if [ $$? -eq 3 ]; then \
			echo "Starting Postgres Server with data dir $(PGDATA)"; pg_ctl -l $(PGDATA)/pg.log -D $(PGDATA) start; \
	else echo "Postgres Server already started for data dir $(PGDATA)"; fi

create-db: check-db ## Create database and user for local development
	@psql -U $(LOGNAME) -e -d postgres -c "CREATE ROLE $(APPNAME) WITH LOGIN PASSWORD '$(APPNAME)';"
	@psql -U $(LOGNAME) -e -d postgres -c "CREATE DATABASE $(APPNAME)_db OWNER $(APPNAME);"
	@psql -U $(LOGNAME) -e -d postgres -c "GRANT ALL PRIVILEGES ON DATABASE $(APPNAME)_db TO $(APPNAME);"
	@echo "Database $(APPNAME)_db and user $(APPNAME) created"
