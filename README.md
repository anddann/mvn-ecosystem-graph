![Maven](https://github.com/anddann/mvn-ecosystem-graph/actions/workflows/maven.yml/badge.svg)

# mvn-ecosystem-graph

The repository contains code for creating a Neo4j database of the artifacts on Maven Central.

## Requirements

* Git >= 2.38.0
* Docker >= 20.10.21
* JDK >= 1.8.0_231
* Maven >= 3.8.6

## Building the project

To build the project and docker containers run

```
mvn clean compile package
```

## Running the Docker Images

To start the docker images

1. copy the env file `cp production.samle.env production.env`
2. adapt the environment variables in `production.env` to your environment
3. Start a Neo4j instance by executing `docker compose -f neo4j-docker-compose.yml up -d`
4. Start the crawler by executing `docker compose -f docker-compose.yml`
5. Wait for the crawlers to do their work