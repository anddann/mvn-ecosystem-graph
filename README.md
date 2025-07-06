![Maven](https://github.com/anddann/mvn-ecosystem-graph/actions/workflows/maven.yml/badge.svg)

# mvn-ecosystem-graph

> A high-performance tool to extract and analyze the dependency relationships within the Maven Central ecosystem.

![License](https://img.shields.io/github/license/anddann/mvn-ecosystem-graph)
![Build](https://img.shields.io/github/actions/workflow/status/anddann/mvn-ecosystem-graph/build.yml)
![Language](https://img.shields.io/github/languages/top/anddann/mvn-ecosystem-graph)

## Overview

**mvn-ecosystem-graph** is a scalable and efficient pipeline for constructing a graph-based representation of Maven artifacts and their dependencies. It allows researchers, developers, and security analysts to:

- Explore the structure of the Maven Central repository
- Analyze transitive dependencies and dependency reachability
- Perform ecosystem-wide analysis (e.g., reverse dependency queries, graph-based ranking, and vulnerability propagation studies)

The resulting graph can be used for further analysis in formats such as Neo4j, CSV, or for integration into static analysis tools.

## Features

- 📦 Parses and resolves Maven POMs with dependency metadata
- ⚡ Fast and scalable — optimized for processing large Maven snapshots
- 🌐 Builds a directed graph of GAV (Group:Artifact:Version) nodes and dependency edges
- 🛠️ Supports optional/optional+runtime dependencies, version resolution, and scope filters
- 🔍 Designed for extensibility in research and software analysis pipelines

## Use Cases

- Software supply chain analysis
- Dependency vulnerability propagation tracking
- Ecosystem evolution and impact studies
- Reverse dependency resolution for build systems

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- Internet access to fetch artifacts from Maven Central (or configure local repo)

### Build

```bash
git clone https://github.com/anddann/mvn-ecosystem-graph.git
cd mvn-ecosystem-graph
mvn package
```

# Maven-Ecosystem-Graph
The repository contains code for creating a Neo4j database of the artifacts on Maven Central. It consists of

* a producer: using a downloadable snapshot of the Maven Central Index, the producer pushes information for relevant
  artifacts (excl. test or src JARS) into a RabbitMQ queue
* worker(s): take messages from the RabbitMQ queue and produce dependency graphs for Neo4j for an artifact, and writes
  to Redis or Neo4j
* redis-task: takes the crawled MvnArtifactNodes from Redis and writes them into Neo43

We **recommend** the use of Redis for the writers. This eases the workers from creating complex Cypher queries or
ensuring uniqueness. Instead, works can then "burst" all information into Redis, and thus can crawl *significant*
faster.

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

## Architecture 
[ Artifact List ]
      ↓
[ POM Fetcher ] ──▶ [ Dependency Resolver ] ──▶ [ Graph Builder ]
                                              ↓
                                    [ Output: CSV / JSON ]
