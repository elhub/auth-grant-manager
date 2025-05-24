# auth-grant-manager

## About

The auth-grant-manager is designed to manage and track user authorizations across Elhub applications, services,
and data processing activities. The goal is to provide a centralized platform for collecting, storing, and
processing user authorization data. The main objectives are to:

* Facilitate the collection of explicit authorization/consent from users for a variety of business and data
    processing activities.
* Provide a centralized repository for storing and managing user authorization preferences.
* Enable granular control over authorization grants, allowing users to specify their preferences for different
    types of data processing activities.
* Ensure compliance with regulatory requirements and standards related to data privacy and consent management.

## Getting Started

### Prerequisites

* Java 17
* Docker
* Environment variables for the database connection: `DB_USERNAME` and `DB_PASSWORD`
    * Run `DB_USERNAME=postgres DB_PASSWORD=postgres ./gradlew ...` to pass the variables to the gradle tasks.

### Building & Running

You can build and run the application locally  using gradle.

| Command                       | Description                                                          |
|-------------------------------|----------------------------------------------------------------------|
| `./gradlew run`               | Run the application (pass DB variables as mentioned above)           |
| `./gradlew build`             | Build everything                                                     |
| `./gradlew test`              | Run the tests                                                        |
| `./gradlew buildFatJar`       | Build an executable JAR of the server with all dependencies included |
| `./gradlew buildImage`        | Build the docker image to use with the fat JAR                       |

If you run this locally, you will need to have a PostgreSQL database running. The following commands allow you
to set up and tear down the database using Docker (Note that both run and test automatically do this under
normal circumstances):

| Command                         | Description                                                          |
|---------------------------------|----------------------------------------------------------------------|
| `./gradlew databaseComposeUp`   | Starts the Postgres container                                        |
| `./gradlew databaseComposeDown` | Stops and removes the Postgres container                             |

Database operations are carried out using Liquibase. To apply changes to the database, run:

| Command                     | Description                                              |
|-----------------------------|----------------------------------------------------------|
| `./gradlew liquibaseUpdate` | Deploy any changes in the changelog file to the database |

### Elhub Dependencies

Note that this project is set up to depend on Elhub's internal package manager (see settings.gradle.kts). If you wish
to build this externally, you will need to substitute this with Maven Central or an alternative with the relevant
packages. Often, it should be sufficient just to replace the elhub repository with `mavenCentral()`.

You will also need access to Elhub's gradle plugins which are not on Maven Central. The plugins are open source,
though, so you can either build them yourself or access them through [Jitpack](https://jitpack.io/).

### Configuration

Modify the application.yaml to adjust server settings or override values using environment variables.

```bash
export PORT=9090
export DATABASE_URL=jdbc:postgresql://localhost:5432/jdbc
```
## Usage

See [the documentation](docs/usage.md) for information on how to use the API.

## API Endpoints

Review the [OpenAPI spec](https://github.com/elhub/auth-grant-manager/blob/main/src/main/resources/openapi.yaml) to
view the available endpoints.

## Contributing

Contributing, issues and feature requests are welcome. See the
[Contributing](https://github.com/elhub/auth-grant-manager/blob/main/.github/CONTRIBUTING.md) file.

## Owners

This project is developed by [Elhub](https://www.elhub.no). For the specific development group responsible for this
code, see the [Codeowners](https://github.com/elhub/auth-grant-manager/blob/main/.github/CODEOWNERS) file.

## License

This project is licensed under the MIT License - see the
[LICENSE](https://github.com/elhub/auth-grant-manager/blob/main/LICENSE) file for details.
