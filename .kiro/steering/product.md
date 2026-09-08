# BookFlow Product Definition

## Product Name

BookFlow

## Product Purpose

BookFlow is a centralized academic resource-sharing web application for college students.

The platform allows registered users to upload academic resources and allows users to discover, view, and download approved academic resources.

## Core Business Rule

Any registered user can upload an academic resource.

There is no senior/junior restriction.

Every user-uploaded resource must initially have the status PENDING.

An administrator must verify every user-uploaded resource.

Only APPROVED resources can appear in the public resource library and be downloaded by users.

Rejected resources must not be publicly accessible.

## Resource Types

BookFlow supports:

- Lecture Notes
- Previous Year Question Papers (PYQs)
- Internal Examination Papers
- Exam Paper Patterns
- Reference Books / Reference Materials

## Student/User Capabilities

A registered user can:

- Register an account
- Login
- Logout
- View and update their profile
- Browse approved resources
- Search resources
- Filter resources
- View resource details
- Download approved resources
- Upload academic resources
- View their own uploaded resources
- View upload status
- See rejection reasons for rejected uploads
- Bookmark resources

## Administrator Capabilities

An administrator can:

- Login
- View dashboard statistics
- Manage users
- View pending resources
- Review uploaded resources
- Approve resources
- Reject resources
- Provide rejection reasons
- Delete inappropriate resources
- Upload official academic resources
- Manage branches
- Manage subjects
- Manage semesters
- Manage resource categories

## Resource Approval Workflow

User uploads resource:

PENDING

Admin reviews resource:

PENDING -> APPROVED

or

PENDING -> REJECTED

Only APPROVED resources are publicly available.

## Main Resource Metadata

Each resource should support:

- Title
- Description
- Resource Type
- Branch
- Year
- Semester
- Subject
- Uploaded By
- File
- Status
- Rejection Reason
- Verified By
- Verification Date
- Created Date
- Updated Date

## Main Application Areas

### Public Area

- Home
- About
- Browse Resources
- Login
- Register

### User Area

- Dashboard
- Resource Library
- Resource Details
- Upload Resource
- My Uploads
- Downloads
- Bookmarks
- Profile

### Admin Area

- Admin Dashboard
- User Management
- Resource Verification
- Resource Management
- Branch Management
- Subject Management
- Category Management

## Product Goal

BookFlow should make academic resources easier to organize, discover, verify, and access from one centralized platform.