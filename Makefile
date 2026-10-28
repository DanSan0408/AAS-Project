# ==============================================================================
# AAS Project (IAMS) - Local Development Makefile
# ==============================================================================
# Usage:
#   make help          Show all available commands
#   make dev           Start complete local stack (MySQL + App + AI in Docker)
#   make dev-db        Start ONLY MySQL database (for local IDE development)
#   make ai-dev        Start LangGraph AI service locally with hot-reload
#   make down          Stop all running services
#   make logs          Follow application logs
#   make db-reset      Reset database while preserving SuperAdmin
#   make check-env     Diagnose local development environment
# ==============================================================================

SHELL := /bin/bash
PROJECT_DIR := $(CURDIR)/adproject
AI_DIR := $(CURDIR)/ai-service
COMPOSE_FILE := $(PROJECT_DIR)/docker-compose.yml
ENV_FILE := $(PROJECT_DIR)/.env
ENV_EXAMPLE := $(PROJECT_DIR)/.env.example
RESET_SQL := $(PROJECT_DIR)/reset_to_superadmin_only.sql

# Detect Docker Compose command (modern 'docker compose' vs legacy 'docker-compose')
DOCKER_COMPOSE := $(shell if docker compose version >/dev/null 2>&1; then echo "docker compose"; else echo "docker-compose"; fi)

# Load MYSQL_ROOT_PASSWORD from .env if available, fallback to default
MYSQL_PASS := $(shell [ -f $(ENV_FILE) ] && grep -E '^MYSQL_ROOT_PASSWORD=' $(ENV_FILE) | cut -d '=' -f2- || echo "rootpassword123")
ifeq ($(strip $(MYSQL_PASS)),)
MYSQL_PASS := rootpassword123
endif

# ANSI color codes for pretty output
CYAN   := \033[36m
GREEN  := \033[32m
YELLOW := \033[33m
RED    := \033[31m
RESET  := \033[0m
BOLD   := \033[1m

.DEFAULT_GOAL := help
.PHONY: help env dev up dev-db db-up down stop restart build rebuild logs logs-app logs-db logs-ai status ps db-shell db-reset test run-local check-env clean ai-setup ai-dev ai-run

# ------------------------------------------------------------------------------
# Help Menu
# ------------------------------------------------------------------------------
help:
	@echo -e "$(BOLD)$(CYAN)=====================================================================$(RESET)"
	@echo -e "$(BOLD)$(CYAN)       IAMS / AAS Project - Local Development Automation Makefile     $(RESET)"
	@echo -e "$(BOLD)$(CYAN)=====================================================================$(RESET)"
	@echo -e "$(BOLD)Core Workflows:$(RESET)"
	@echo -e "  $(GREEN)make dev$(RESET)          Start full environment in Docker (MySQL + App + AI)"
	@echo -e "  $(GREEN)make dev-db$(RESET)       Start ONLY MySQL container (for running App in IDE)"
	@echo -e "  $(GREEN)make down$(RESET)         Stop all containers"
	@echo -e "  $(GREEN)make restart$(RESET)      Restart all containers"
	@echo -e ""
	@echo -e "$(BOLD)AI Service (LangGraph + Gemini):$(RESET)"
	@echo -e "  $(GREEN)make ai-setup$(RESET)     Install Python venv & dependencies for AI service"
	@echo -e "  $(GREEN)make ai-dev$(RESET)       Start local LangGraph/FastAPI server (port 8000, reload)"
	@echo -e "  $(GREEN)make logs-ai$(RESET)      Follow AI microservice container logs"
	@echo -e ""
	@echo -e "$(BOLD)Monitoring & Logs:$(RESET)"
	@echo -e "  $(GREEN)make logs$(RESET)         Follow all container logs"
	@echo -e "  $(GREEN)make logs-app$(RESET)     Follow Spring Boot application logs only"
	@echo -e "  $(GREEN)make logs-db$(RESET)      Follow MySQL database logs only"
	@echo -e "  $(GREEN)make status$(RESET)       Display status of containers and open ports"
	@echo -e ""
	@echo -e "$(BOLD)Database Management:$(RESET)"
	@echo -e "  $(GREEN)make db-shell$(RESET)     Launch interactive MySQL shell inside the container"
	@echo -e "  $(GREEN)make db-reset$(RESET)     Run reset script (wipes data, preserves SuperAdmin)"
	@echo -e ""
	@echo -e "$(BOLD)Build & Diagnostics:$(RESET)"
	@echo -e "  $(GREEN)make build$(RESET)        Build application Docker container"
	@echo -e "  $(GREEN)make rebuild$(RESET)      Rebuild application container without Docker cache"
	@echo -e "  $(GREEN)make test$(RESET)         Run tests (in container if local Java missing)"
	@echo -e "  $(GREEN)make check-env$(RESET)    Diagnose local development tools & ports"
	@echo -e "  $(GREEN)make env$(RESET)          Ensure .env exists with local defaults"
	@echo -e "  $(GREEN)make clean$(RESET)        Clean build targets and stale artifacts"
	@echo -e "$(CYAN)=====================================================================$(RESET)"

# ------------------------------------------------------------------------------
# Environment Setup
# ------------------------------------------------------------------------------
env:
	@if [ ! -f $(ENV_FILE) ]; then \
		echo -e "$(YELLOW)[i] Creating $(ENV_FILE) from .env.example...$(RESET)"; \
		cp $(ENV_EXAMPLE) $(ENV_FILE); \
		sed -i '' 's/MYSQL_ROOT_PASSWORD=replace-me/MYSQL_ROOT_PASSWORD=rootpassword123/' $(ENV_FILE) 2>/dev/null || \
		sed -i 's/MYSQL_ROOT_PASSWORD=replace-me/MYSQL_ROOT_PASSWORD=rootpassword123/' $(ENV_FILE); \
		echo -e "$(GREEN)[✓] Initialized $(ENV_FILE) with default MySQL password: rootpassword123$(RESET)"; \
	else \
		echo -e "$(GREEN)[✓] Environment file $(ENV_FILE) already exists.$(RESET)"; \
	fi

# ------------------------------------------------------------------------------
# Development Commands (Docker)
# ------------------------------------------------------------------------------
dev: up

up: env
	@echo -e "$(CYAN)[*] Starting MySQL and Spring Boot App via Docker Compose...$(RESET)"
	@cd $(PROJECT_DIR) && $(DOCKER_COMPOSE) up -d --build
	@echo -e "$(GREEN)[✓] Stack started!$(RESET)"
	@echo -e "$(BOLD)Application:$(RESET)  http://localhost:8080"
	@echo -e "$(BOLD)MySQL DB:$(RESET)     localhost:3306 (database: aas_db, user: root)"
	@echo -e "$(YELLOW)[i] Hint: Run 'make logs' to monitor application startup.$(RESET)"

dev-db: db-up

db-up: env
	@echo -e "$(CYAN)[*] Starting ONLY MySQL database in Docker...$(RESET)"
	@cd $(PROJECT_DIR) && $(DOCKER_COMPOSE) up -d mysql
	@echo -e "$(GREEN)[✓] MySQL is starting at localhost:3306!$(RESET)"
	@echo -e "$(YELLOW)[i] You can now run the Spring Boot app from your IDE (VS Code, IntelliJ) or 'make run-local'.$(RESET)"

down:
	@echo -e "$(YELLOW)[*] Stopping all Docker containers...$(RESET)"
	@cd $(PROJECT_DIR) && $(DOCKER_COMPOSE) down
	@echo -e "$(GREEN)[✓] All containers stopped.$(RESET)"

stop: down

restart: down up

# ------------------------------------------------------------------------------
# Logs & Monitoring
# ------------------------------------------------------------------------------
logs:
	@cd $(PROJECT_DIR) && $(DOCKER_COMPOSE) logs -f

logs-app:
	@cd $(PROJECT_DIR) && $(DOCKER_COMPOSE) logs -f app

logs-db:
	@cd $(PROJECT_DIR) && $(DOCKER_COMPOSE) logs -f mysql

logs-ai:
	@cd $(PROJECT_DIR) && $(DOCKER_COMPOSE) logs -f ai-service

status: ps

ps:
	@echo -e "$(BOLD)$(CYAN)Docker Containers:$(RESET)"
	@cd $(PROJECT_DIR) && $(DOCKER_COMPOSE) ps
	@echo -e "\n$(BOLD)$(CYAN)Local Port Listening Status:$(RESET)"
	@lsof -i :8080 -i :3306 -i :8000 2>/dev/null || echo -e "Ports 8080, 3306, and 8000 are currently free."

# ------------------------------------------------------------------------------
# Database Operations
# ------------------------------------------------------------------------------
db-shell:
	@echo -e "$(CYAN)[*] Connecting to MySQL CLI inside 'adproject-mysql'...$(RESET)"
	@docker exec -it adproject-mysql mysql -u root -p$(MYSQL_PASS) aas_db

db-reset:
	@echo -e "$(YELLOW)[!] Resetting database using $(RESET_SQL)...$(RESET)"
	@if [ ! -f $(RESET_SQL) ]; then \
		echo -e "$(RED)[x] Reset script $(RESET_SQL) not found.$(RESET)"; exit 1; \
	fi
	@docker exec -i adproject-mysql mysql -u root -p$(MYSQL_PASS) aas_db < $(RESET_SQL)
	@echo -e "$(GREEN)[✓] Operational data wiped and SuperAdmin verified successfully.$(RESET)"

# ------------------------------------------------------------------------------
# Build & Local Run
# ------------------------------------------------------------------------------
build:
	@echo -e "$(CYAN)[*] Building Docker image for application...$(RESET)"
	@cd $(PROJECT_DIR) && $(DOCKER_COMPOSE) build

rebuild:
	@echo -e "$(CYAN)[*] Rebuilding Docker image without cache...$(RESET)"
	@cd $(PROJECT_DIR) && $(DOCKER_COMPOSE) build --no-cache

test:
	@echo -e "$(CYAN)[*] Running tests...$(RESET)"
	@if java -version >/dev/null 2>&1 && [ -f $(PROJECT_DIR)/mvnw ]; then \
		echo -e "$(GREEN)[*] Running tests locally via Maven wrapper...$(RESET)"; \
		cd $(PROJECT_DIR) && ./mvnw test; \
	else \
		echo -e "$(YELLOW)[i] Local Java/Maven runtime not detected. Running tests inside Docker container...$(RESET)"; \
		docker run --rm -v $(PROJECT_DIR):/workspace -w /workspace maven:3.9-eclipse-temurin-17 mvn test; \
	fi

run-local: db-up
	@echo -e "$(CYAN)[*] Checking local Java environment...$(RESET)"
	@if java -version >/dev/null 2>&1; then \
		echo -e "$(GREEN)[*] Launching Spring Boot locally via Maven wrapper...$(RESET)"; \
		cd $(PROJECT_DIR) && ./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.datasource.password=$(MYSQL_PASS)"; \
	else \
		echo -e "$(RED)[x] Local Java runtime not found.$(RESET)"; \
		echo -e "$(YELLOW)[i] To run directly on macOS host, install Java 17 via Homebrew:$(RESET)"; \
		echo -e "      brew install openjdk@17"; \
		echo -e "      sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk"; \
		echo -e "$(YELLOW)[i] Alternatively, simply run in Docker:$(RESET)"; \
		echo -e "      make dev"; \
	fi

# ------------------------------------------------------------------------------
# AI Service (LangGraph + Gemini)
# ------------------------------------------------------------------------------
ai-setup:
	@echo -e "$(CYAN)[*] Setting up Python virtual environment for ai-service...$(RESET)"
	@if [ ! -d "$(AI_DIR)/.venv" ]; then \
		python3 -m venv $(AI_DIR)/.venv; \
	fi
	@echo -e "$(CYAN)[*] Installing AI service dependencies into $(AI_DIR)/.venv...$(RESET)"
	@$(AI_DIR)/.venv/bin/pip install -r $(AI_DIR)/requirements.txt
	@echo -e "$(GREEN)[✓] AI microservice dependencies installed into $(AI_DIR)/.venv$(RESET)"

ai-dev: env
	@echo -e "$(CYAN)[*] Starting LangGraph / FastAPI AI Service (port 8000)...$(RESET)"
	@if [ ! -d "$(AI_DIR)/.venv" ]; then \
		echo -e "$(YELLOW)[i] Virtual environment missing. Running 'make ai-setup'...$(RESET)"; \
		$(MAKE) ai-setup; \
	fi
	@cd $(AI_DIR) && .venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload

ai-run: ai-dev

# ------------------------------------------------------------------------------
# Diagnostics & Healthcheck
# ------------------------------------------------------------------------------
check-env:
	@echo -e "$(BOLD)$(CYAN)=== Environment Health Diagnostic ===$(RESET)"
	@echo -n "Docker CLI:           "
	@if command -v docker >/dev/null 2>&1; then \
		echo -e "$(GREEN)[OK] $$(docker --version)$(RESET)"; \
	else \
		echo -e "$(RED)[FAIL] Docker is not installed or not in PATH.$(RESET)"; \
	fi
	@echo -n "Docker Compose:       "
	@if docker compose version >/dev/null 2>&1; then \
		echo -e "$(GREEN)[OK] $$(docker compose version)$(RESET)"; \
	else \
		echo -e "$(RED)[FAIL] Docker Compose not found.$(RESET)"; \
	fi
	@echo -n "Docker Daemon:        "
	@if docker info >/dev/null 2>&1; then \
		echo -e "$(GREEN)[OK] Docker daemon is running.$(RESET)"; \
	else \
		echo -e "$(RED)[FAIL] Docker daemon is not running. Please start Docker Desktop.$(RESET)"; \
	fi
	@echo -n "Python 3 Runtime:     "
	@if python3 --version >/dev/null 2>&1; then \
		echo -e "$(GREEN)[OK] $$(python3 --version)$(RESET)"; \
	else \
		echo -e "$(RED)[FAIL] Python 3 not found on host.$(RESET)"; \
	fi
	@echo -n "AI Service Venv:      "
	@if [ -d "$(AI_DIR)/.venv" ]; then \
		echo -e "$(GREEN)[OK] Present at $(AI_DIR)/.venv$(RESET)"; \
	else \
		echo -e "$(YELLOW)[OPTIONAL] Missing. Run 'make ai-setup' to configure.$(RESET)"; \
	fi
	@echo -n "Gemini API Key:       "
	@if [ -f $(ENV_FILE) ] && grep -E '^GEMINI_API_KEY=[a-zA-Z0-9_-]+' $(ENV_FILE) >/dev/null 2>&1; then \
		echo -e "$(GREEN)[OK] Configured in $(ENV_FILE)$(RESET)"; \
	else \
		echo -e "$(YELLOW)[WARN] Missing or empty in $(ENV_FILE). Add GEMINI_API_KEY for AI features.$(RESET)"; \
	fi
	@echo -n "Local Java 17:        "
	@if java -version >/dev/null 2>&1; then \
		echo -e "$(GREEN)[OK] $$(java -version 2>&1 | head -n 1)$(RESET)"; \
	else \
		echo -e "$(YELLOW)[OPTIONAL] Not detected on host OS. (Docker workflow 'make dev' available).$(RESET)"; \
	fi
	@echo -n "Environment File:     "
	@if [ -f $(ENV_FILE) ]; then \
		echo -e "$(GREEN)[OK] $(ENV_FILE) present.$(RESET)"; \
	else \
		echo -e "$(YELLOW)[WARN] $(ENV_FILE) missing. Run 'make env' to generate it.$(RESET)"; \
	fi
	@echo -n "Port 8080 (App):      "
	@if lsof -i :8080 >/dev/null 2>&1; then \
		echo -e "$(YELLOW)[BUSY] Port 8080 is currently in use.$(RESET)"; \
	else \
		echo -e "$(GREEN)[FREE] Available$(RESET)"; \
	fi
	@echo -n "Port 3306 (MySQL):    "
	@if lsof -i :3306 >/dev/null 2>&1; then \
		echo -e "$(YELLOW)[BUSY] Port 3306 is currently in use.$(RESET)"; \
	else \
		echo -e "$(GREEN)[FREE] Available$(RESET)"; \
	fi
	@echo -n "Port 8000 (AI API):   "
	@if lsof -i :8000 >/dev/null 2>&1; then \
		echo -e "$(YELLOW)[BUSY] Port 8000 is currently in use.$(RESET)"; \
	else \
		echo -e "$(GREEN)[FREE] Available$(RESET)"; \
	fi
	@echo -e "$(BOLD)$(CYAN)=====================================$(RESET)"

# ------------------------------------------------------------------------------
# Cleanup
# ------------------------------------------------------------------------------
clean:
	@echo -e "$(YELLOW)[*] Cleaning Maven build targets...$(RESET)"
	@rm -rf $(PROJECT_DIR)/target
	@echo -e "$(GREEN)[✓] Target directory cleaned.$(RESET)"
