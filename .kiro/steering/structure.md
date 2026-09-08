# BookFlow Project Structure

The project should follow a clean Spring Boot layered architecture.

## Backend Packages

Use a root package similar to:

com.bookflow

Under it:

- controller
- service
- repository
- entity
- dto
- config
- exception

## Frontend

Organize frontend resources clearly.

Static resources:

- CSS
- JavaScript
- Images

HTML/templates should be organized by application area where appropriate.

## Database

Database entities should represent the core BookFlow concepts:

- User
- Branch
- Subject
- Resource
- Download
- Bookmark

## Resource Management

Resource-related functionality should remain separated from authentication and user-management functionality.

## Administration

Admin-specific functionality should be protected by role-based authorization.

## File Storage

Keep uploaded files outside the normal static public web directory.

## General Rule

Do not create unnecessary packages or abstractions.

Prefer a simple, understandable architecture appropriate for a student Java web application.