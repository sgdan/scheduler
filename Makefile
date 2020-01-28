.PHONY: build

# build and run using docker
build:
	docker build . -t scheduler

run: build
	docker run --rm -it -v ~/.aws:/root/.aws -p 8080:8080 scheduler

shell: build
	docker run --rm -it scheduler sh

# for local development, start front and back end separately
frontend-dev:
	cd frontend && elm-app start

backend-dev:
	docker-compose run -p 8080:8080 gradle gradle run


# unit testing
frontend-test:
	cd frontend && elm-test

backend-test:
	docker-compose run --rm gradle gradle test


# gradle shell for back end
backend-shell:
	docker-compose run --rm -p 8080:8080 gradle bash
