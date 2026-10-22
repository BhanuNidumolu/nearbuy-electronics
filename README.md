# NearBuy Electronics

NearBuy is a local electronics marketplace that helps customers discover
offers from nearby retailers, compare prices, and reserve products for
pickup. It gives local shops a way to publish inventory and manage
customer reservations without losing the convenience customers expect
from online shopping.

**Live demo:** https://nearbuy-electronics.onrender.com\

**Source code:** https://github.com/BhanuNidumolu/nearbuy-electronics

> The live service runs on free-tier hosting. It may take a little time
> to respond after a period of inactivity.

## Screenshots

Add genuine screenshots from the running application to the
`docs/screenshots/` directory in this repository. Replace the
placeholder filenames below with your actual screenshot filenames.

### Home page

<img width="943" height="499" alt="image" src="https://github.com/user-attachments/assets/57dcb7e7-58b7-4316-9b82-7d70b6001fa3" />

### Browse offers

<img width="921" height="482" alt="image" src="https://github.com/user-attachments/assets/162e1280-ad47-43f9-a6f8-b330ae6976e6" />


### Product search and nearby results

<img width="903" height="392" alt="image" src="https://github.com/user-attachments/assets/2e417acd-6bb2-4962-8374-0ec9c572428b" />


### Login and account access

<img width="649" height="479" alt="image" src="https://github.com/user-attachments/assets/3230f1b2-ddf7-402f-9b8c-f24ffa154bd2" />


### Reservation flow

<img width="885" height="464" alt="image" src="https://github.com/user-attachments/assets/cd351582-0bee-4e69-8c39-5a8a14035cfe" />


## What it does

-   **Nearby offers:** Browse available electronics offers and filter
    results by product query or location.
-   **Price discovery:** Compare offers returned for the same product
    search.
-   **Customer accounts:** Register and authenticate using JWT-based
    authentication.
-   **Retailer workflows:** Retailers manage shop details and inventory,
    subject to verification rules.
-   **Reservations:** Customers can reserve available stock for pickup.
    Reservations have a configurable hold duration and can be cancelled.
-   **Address-change review:** Retailers can request changes to a
    verified shop address; the request is reviewed before the public
    address changes.
-   **Role-based access:** Customer, retailer, and administrator actions
    are protected by backend authorization rules.

## How it fits together

``` mermaid
flowchart TD
    Customer[Customer / Retailer / Admin] --> UI[React frontend]
    UI --> API[Spring Boot REST API]
    API --> Auth[Spring Security + JWT]
    API --> Domain[Offers, inventory and reservations]
    Domain --> DB[(MySQL)]
    API --> Migrations[Flyway migrations]
```

The frontend is built as part of the Docker image and copied into Spring
Boot's static resources. The deployed application therefore serves the
web interface and API from the same service.

## Technology

  -----------------------------------------------------------------------
  Area                                Technologies
  ----------------------------------- -----------------------------------
  Frontend                            React, Vite

  Backend                             Java 21, Spring Boot, Spring
                                      Security, Spring Data JPA /
                                      Hibernate

  API security                        JWT authentication and role-based
                                      authorization

  Database                            MySQL 8.4, Flyway schema migrations

  Packaging                           Docker, Docker Compose, multi-stage
                                      Dockerfile

  Hosting                             Render web service and Aiven MySQL
  -----------------------------------------------------------------------

## Run it locally

### Prerequisites

-   Docker Desktop with Docker Compose
-   Git

### Start the application

Clone the repository and run Compose from its root:

``` bash
git clone https://github.com/BhanuNidumolu/nearbuy-electronics.git
cd nearbuy-electronics
docker compose up --build
```

The Compose configuration builds the frontend and backend, starts MySQL,
and launches the application. The application is exposed locally at:

-   Web application: http://localhost:8083
-   Health endpoint: http://localhost:8083/api/health
-   Offers endpoint: http://localhost:8083/api/offers

The first build can take a few minutes while Docker downloads
dependencies and builds the images.

To stop the local services while preserving the MySQL volume:

``` bash
docker compose down
```

**Avoid `docker compose down -v` unless you deliberately intend to
delete the Compose database volume and its data.**

### Local configuration

The Compose file provides local development defaults for the MySQL
database and application. For anything beyond local development, set
your own credentials and a strong JWT secret through environment
variables. Never commit `.env` files, production credentials, tokens, or
private keys.

## API quick checks

With the application running locally, these requests are useful for a
first check:

``` bash
# Application health
curl http://localhost:8083/api/health

# Browse offers
curl http://localhost:8083/api/offers
```

Search and location filters are supported by the offers endpoint. For
example:

``` text
/api/offers?q=Samsung
/api/offers?latitude=16.3067&longitude=80.4365
```

The exact request bodies and authorization requirements depend on the
endpoint. Use the controllers in `backend/src/main/java` as the source
of truth when extending or integrating the API.

## Production deployment

The public demo is deployed as a Docker web service on Render, with
MySQL hosted separately on Aiven.

The production service uses environment variables for its database
connection and JWT signing secret. It also selects the dedicated
production migration location:

``` text
SPRING_FLYWAY_LOCATIONS=classpath:db/migration-prod
```

That location intentionally keeps development/demo seed migrations out
of the production database. Keep the production database URL, username,
password, JWT secret, and bootstrap-admin credentials in the hosting
provider's secret configuration---not in this README or source control.

Free hosting has limitations: services can become idle, the first
request after inactivity may be slow, and database storage is limited by
the provider's free plan. This deployment is a portfolio/demo
environment, not a promise of production-grade availability.

## Project layout

``` text
.
├── backend/             # Spring Boot API, persistence, security and migrations
├── frontend/            # React/Vite user interface
├── Dockerfile           # Multi-stage frontend + backend image
├── docker-compose.yml   # Local app and MySQL development environment
├── README.md
└── README-continued.md  # Notes from a previous UI refinement pass
```

## Development notes

-   Keep schema changes in versioned Flyway migrations.
-   Test changes locally before pushing them to `main`; Render deploys
    from the repository's configured branch.
-   Keep local and production migration locations separate. Do not move
    demo-data migrations into the production migration path.
-   Do not commit credentials. If a secret is exposed, rotate it at the
    provider and update the deployed environment variables.

## Current scope and next steps

NearBuy is a working project under active development. Areas worth
improving next include adding current screenshots to this README,
expanding endpoint documentation, and adding an automated deployment
check for the production health endpoint.

## License

No license file is currently documented in this repository. Unless a
license is added, reuse and redistribution rights should not be assumed.
