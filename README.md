# 🎓 Integrated External Assessment System

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.4-brightgreen.svg)
![Java](https://img.shields.io/badge/Java-17-orange.svg)
![MySQL](https://img.shields.io/badge/MySQL-8.4-blue.svg)
![Docker](https://img.shields.io/badge/Docker-Supported-blueviolet.svg)

Welcome to the **Integrated External Assessment System**, a comprehensive, full-stack web application designed to streamline the administration, evaluation, and tracking of academic capstone projects. Built with **Spring Boot** and **Java 17**, this robust platform provides targeted portals for Administrators, Lecturers, Students, and Industrial Supervisors.

---


## 💻 Tech Stack

* **Backend:** Java 17, Spring Boot 3.5.4, Spring Security, Spring Data JPA, Hibernate.
* **Frontend:** HTML5, CSS3, Thymeleaf (Server-side rendering).
* **Database:** MySQL 8.4.
* **Deployment & Containerization:** Docker, Docker Compose.
* **Build Tool:** Maven.

---

## 🛠️ Getting Started

Follow these instructions to get a copy of the project up and running on your local machine for development and testing purposes.

### Prerequisites

Ensure you have the following installed:
* [Docker](https://www.docker.com/products/docker-desktop/) (Recommended for easy setup)
* *Alternative (for manual setup):* [Java 17 JDK](https://adoptium.net/), [Maven](https://maven.apache.org/), and [MySQL 8.4](https://dev.mysql.com/downloads/mysql/)

---

### Option 1: Run with Docker (Recommended) 🐳

The easiest way to get the application running is by using Docker Compose. This will automatically spin up the Spring Boot app and the MySQL database in linked containers.

1. **Clone the repository:**
   ```bash
   git clone https://github.com/your-username/AAS-Project-Main.git
   cd AAS-Project-Main/adproject
   ```

2. **Configure Environment Variables:**
   Copy the example environment file and configure your credentials (like `MYSQL_ROOT_PASSWORD`):
   ```bash
   cp .env.example .env
   ```
   *(Make sure to open `.env` and fill in any required passwords and email configurations if you wish to test the email features).*

3. **Build and Run the Containers:**
   ```bash
   docker-compose up -d --build
   ```

4. **Access the Application:**
   Open your browser and navigate to: `http://localhost:8080`

To stop the application, run: `docker-compose down`

---

### Option 2: Run Locally (Manual Setup) 💻

If you prefer to run the application natively on your machine:

1. **Set up the Database:**
   * Open your local MySQL instance.
   * Create a new database named `aas_db`.

2. **Configure Application Properties:**
   * Navigate to `adproject/src/main/resources/application.properties`.
   * Update the `spring.datasource.password` and `spring.mail.*` properties with your local MySQL password and valid SMTP credentials.

3. **Run the Application:**
   * Open a terminal in the `adproject` directory.
   * Use the Maven wrapper to start the app:
     ```bash
     # On Windows
     mvnw.cmd spring-boot:run
     
     # On macOS/Linux
     ./mvnw spring-boot:run
     ```

4. **Access the Application:**
   Open your browser and navigate to: `http://localhost:8080`

---

## 📂 Project Structure Overview

```text
AAS-Project-Main/
├── adproject/                  # Main Spring Boot Application Directory
│   ├── src/main/java/          # Java Source Code (Controllers, Services, Models, Repositories)
│   ├── src/main/resources/     # Application Properties, Thymeleaf Templates, Static Assets (CSS/JS)
│   ├── Dockerfile              # Docker Configuration for the App
│   ├── docker-compose.yml      # Multi-container Docker Setup
│   └── pom.xml                 # Maven Dependencies
└── README.md                   # Project Documentation
```

*(Note: The codebase utilizes standard layered architecture: Controllers → Services → Repositories → DB).*

---

## 🤝 Contributing

If you'd like to contribute, please fork the repository and use a feature branch. Pull requests are warmly welcome.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📞 Contact

**Danial Ihsan Bin Mohd Nadhir**  
[LinkedIn](https://www.linkedin.com/in/danial-ihsan-mohd-nadhir-66635b3a8/) | [GitHub](https://github.com/DanSan0408) | [Email](mailto:san.dan0408@gmail.com)

---
*Thank you for reviewing my code! I look forward to connecting.*