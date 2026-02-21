.PHONY: local-up local-down test-up test-down multi-up multi-down multi-build

## ========================
## Local DB
## ========================

local-up:
	docker compose -p ticket-local --env-file ./docker/.env -f ./docker/docker-compose-local.yml up -d

local-down:
	docker compose -p ticket-local --env-file ./docker/.env -f ./docker/docker-compose-local.yml down

## ========================
## Test DB
## ========================

test-up:
	docker compose -p ticket-test --env-file ./docker/.env -f ./docker/docker-compose-test.yml up -d

test-down:
	docker compose -p ticket-test --env-file ./docker/.env -f ./docker/docker-compose-test.yml down

## ========================
## Multi-node (Performance Test)
## ========================

multi-up:
	docker compose -p ticket-multi -f ./docker/docker-compose-multi-node-local.yml up -d

multi-down:
	docker compose -p ticket-multi -f ./docker/docker-compose-multi-node-local.yml down

multi-build:
	docker compose -p ticket-multi -f ./docker/docker-compose-multi-node-local.yml up -d --build