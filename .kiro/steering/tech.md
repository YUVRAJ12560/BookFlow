# BookFlow Technology Stack

## Backend

- Java
- Spring Boot
- Spring MVC
- Spring Data JPA
- Spring Security

## Database

- MySQL

## Frontend

- HTML
- CSS
- JavaScript
- Bootstrap

## Build Tool

- Maven

## File Storage

Use local server-side file storage for the initial implementation.

Uploaded files must not be publicly accessible before resource approval.

## Architecture

Use a clean layered architecture:

- Controller
- Service
- Repository
- Entity
- DTO
- Configuration
- Exception handling

## Database Access

Use Spring Data JPA for database access.

Do not use raw SQL throughout the application when a suitable JPA approach is available.

## Authentication

Use Spring Security.

Passwords must be securely hashed.

Users must not be able to assign themselves the ADMIN role.

## API Design

Use REST-style endpoints where appropriate.

Keep controllers thin.

Business logic belongs in service classes.

Database access belongs in repository classes.

## File Upload Security

Validate uploaded files.

Initially support:

- PDF
- DOC
- DOCX

Do not allow executable files.

Use server-generated file names.

Do not expose physical server file paths.

Only approved resources may be downloaded through the application.

## Development Principles

- Keep code modular.
- Avoid unnecessary complexity.
- Use meaningful class and variable names.
- Validate user input.
- Handle errors properly.
- Do not duplicate business logic.
- Keep authentication and authorization separate from business logic.
- Write maintainable code suitable for a Java academic project.