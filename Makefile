run: ## mvn spring-boot:run
	mvn spring-boot:run

build: ## Build Spring Boot JAR with tests
	mvn clean package

build-skip-tests: ## Build Spring Boot JAR without tests
	mvn clean package -DskipTests

jar: build ## Alias for build target
