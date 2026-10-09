# 📚 BookFlow — Academic Resource Sharing Platform

**BookFlow** is a web-based academic resource-sharing platform designed for IT students to discover, share, and access verified study materials in one place. It simplifies access to lecture notes, previous year question papers, internal examination papers, exam patterns, and reference books.

🌐 **Live Demo:** [https://bookflow-production-20fe.up.railway.app/](https://bookflow-production-20fe.up.railway.app/)

---

## ✨ Features

- **User Authentication** — Register, log in, and manage your account.
- **Google Sign-In** — Authenticate using Google.
- **College Email Verification** — Registration is restricted to college email addresses ending in `@pvppcoe.ac.in`.
- **Academic Resource Library** — Browse and discover study materials by category.
- **Search and Filters** — Find resources using keywords, resource type, subject, year, and semester.
- **Resource Uploads** — Upload academic materials for review.
- **Admin Approval System** — Administrators can approve or reject submitted resources with a reason.
- **Verified Resource Access** — Approved resources are available to authenticated users.
- **Personal Resource Dashboard** — Track uploaded resources and their approval status.
- **Admin Dashboard** — Review submissions and manage academic resources.
- **Responsive Interface** — Designed for desktop and mobile devices.

## 📖 Resource Categories

BookFlow supports five academic resource categories:

| Category | Description |
|---|---|
| 📝 Lecture Notes | Class notes and subject summaries |
| 📄 Previous Year Papers | Previous examination question papers |
| 📋 Internal Exams | Internal assessment and examination papers |
| 📊 Paper Patterns | Examination formats and marking schemes |
| 📚 Reference Books | Recommended academic reading materials |

## 🔄 How It Works

1. **Register or sign in** using an eligible college email or Google Sign-In.
2. **Explore the library** to find relevant academic resources.
3. **Upload study materials** to contribute to the student community.
4. **Wait for admin review** — newly submitted user resources remain pending until reviewed.
5. **Access approved resources** and download materials for your studies.

## 🛠️ Technology Stack

- **Backend:** Java, Spring Boot
- **Frontend:** Thymeleaf, HTML, CSS, JavaScript
- **Database:** MySQL
- **Security:** Spring Security, BCrypt password hashing
- **Authentication:** Email/password authentication and Google Sign-In
- **Email Verification:** Gmail API with OAuth 2.0
- **Build Tool:** Apache Maven
- **Deployment:** Railway

## 🏗️ Project Structure

```text
BookFlow/
├── src/
│   └── main/
│       ├── java/com/bookflow/
│       │   ├── config/
│       │   ├── controller/
│       │   ├── dto/
│       │   ├── entity/
│       │   ├── repository/
│       │   └── service/
│       └── resources/
│           ├── static/
│           ├── templates/
│           └── application.properties
├── uploads/
├── secrets/
├── pom.xml
└── README.md
```

## 🚀 Run Locally

### Prerequisites

- Java JDK compatible with the project's configured Java version
- Apache Maven
- MySQL Server
- Git

### 1. Clone the repository

```bash
git clone https://github.com/YUVRAJ12560/BookFlow.git
cd BookFlow
```

### 2. Create the database

Open MySQL and run:

```sql
CREATE DATABASE bookflow;
```

### 3. Configure environment variables

Configure your local database password:

```bash
export BOOKFLOW_DB_PASSWORD="your_mysql_password"
```

Configure your Google Web OAuth Client ID for Google Sign-In:

```bash
export GOOGLE_OAUTH_CLIENT_ID="your_google_web_client_id"
```

Set the corresponding values in your local Spring Boot configuration as required.

For Google Sign-In, configure the authorized JavaScript origin for your local application, such as `http://localhost:8080`.

Email verification also requires the Gmail API OAuth credentials and token configuration. Keep all credentials and tokens private.

### 4. Build the application

```bash
mvn clean package
```

### 5. Start BookFlow

```bash
mvn spring-boot:run
```

Open the application at:

**http://localhost:8080**

## 🔐 Security

- Passwords are hashed using BCrypt.
- College email restrictions are enforced by the backend.
- Google ID tokens are verified by the server.
- Resource uploads are subject to file validation and size limits.
- User-submitted resources require administrative approval before becoming publicly available to authenticated users.
- OAuth credentials, passwords, and tokens must not be committed to GitHub.

## 🎯 Project Goal

BookFlow aims to make academic collaboration easier by providing students with a centralized platform to discover, contribute, and share useful study materials while maintaining resource quality through administrative verification.

## 👨‍💻 Author

**Yuvraj Upadhyay**

GitHub: [@YUVRAJ12560](https://github.com/YUVRAJ12560)

## 📄 License

This project is available for educational and portfolio purposes. Add a `LICENSE` file to the repository if you intend to distribute it under a specific open-source license.
