# auth-grant-manager

## About

The auth-grant-manager is designed to manage and track user consent preferences across Elhub applications,
services, and data processing activities. The goal is to provide a centralized platform for collecting, storing,
and processing user consent data. The main objectives of the consent manager are to:

* Facilitate the collection of explicit consent from users for a variety of business and data processing activities.
* Provide a centralized repository for storing and managing user consent preferences.
* Enable granular control over consent settings, allowing users to specify their preferences for different types of
    data processing activities.
* Ensure compliance with regulatory requirements and standards related to data privacy and consent management.

## Getting Started

### Prerequisites

* Java 17
* Docker
* Environment variables for the database connection: `DB_USERNAME` and `DB_PASSWORD`
    * Run `DB_USERNAME=postgres DB_PASSWORD=postgres ./gradlew ...` to pass the variables to the gradle tasks

### Building & Running

You can build and run the application using gradle.

| Command                       | Description                                                          |
|-------------------------------|----------------------------------------------------------------------|
| `./gradlew run`               | Run the application                                                  |
| `./gradlew build`             | Build everything                                                     |
| `./gradlew test`              | Run the tests                                                        |
| `./gradlew buildFatJar`       | Build an executable JAR of the server with all dependencies included |
| `./gradlew buildImage`        | Build the docker image to use with the fat JAR                       |

If you run this locally, you will need to have a PostgreSQL database running. The following commands allow you
to set up and tear down the database using Docker:

| Command                         | Description                                                          |
|---------------------------------|----------------------------------------------------------------------|
| `./gradlew databaseComposeUp`   | Starts the Postgres container                                        |
| `./gradlew databaseComposeDown` | Stops and removes the Postgres container                             |

Database operations are carried out using Liquibase. To apply changes to the database, run:

| Command                     | Description                                              |
|-----------------------------|----------------------------------------------------------|
| `./gradlew liquibaseUpdate` | Deploy any changes in the changelog file to the database |

### Configuration

Modify the application.yaml to adjust server settings or override values using environment variables.

```bash
export PORT=9090
export DATABASE_URL=jdbc:postgresql://localhost:5432/jdbc
```

## API Endpoints

The following endpoints are available:

| Method | Path                   | Description                        |
|--------|------------------------|------------------------------------|
| POST   | /authorization-request | Set up a new authorization request |


## Contributing

Contributing, issues and feature requests are welcome. See the
[Contributing](https://github.com/elhub/auth-grant-manager/blob/main/.github/CONTRIBUTING.md) file.

## Owners

This project is developed by [Elhub](https://www.elhub.no). For the specific development group responsible for this
code, see the [Codeowners](https://github.com/elhub/auth-grant-manager/blob/main/.github/CODEOWNERS) file.

## License

This project is licensed under the MIT License - see the
[LICENSE](https://github.com/elhub/auth-grant-manager/blob/main/LICENSE) file for details.
