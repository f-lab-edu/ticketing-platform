.PHONY: local-up local-down test-up test-down

## ========================
## Local DB
## ========================

local-up:
	docker compose -f ./docker/docker-compose-local.yml up -d

local-down:
	docker compose -f ./docker/docker-compose-local.yml down

## ========================
## Test DB
## ========================

test-up:
	docker compose --env-file ./docker/.env.test -f ./docker/docker-compose-test.yml up -d

test-down:
	docker compose --env-file ./docker/.env.test -f ./docker/docker-compose-test.yml down
