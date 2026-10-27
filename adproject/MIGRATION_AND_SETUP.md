# Development Migration & Dummy Data Setup Guide

This guide describes how to bring this project, database, and dummy data over to your other device.

---

## 1. What Has Been Exported
1. **Database Dump File**: [`adproject/aas_db_dump.sql`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/aas_db_dump.sql)
   - Contains the full `aas_db` schema, constraints, and all dummy data:
     - Admin, Lecturer, Student, and Superadmin accounts
     - Courses, Groups, and Student Assignments
     - Assessments, Rubrics, Sub-Rubrics, and Mark allocations
     - Student Report Submissions (`assessment_submissions`)
     - Assessor Vector Ink Doodles & Text Annotations (`submission_annotations`)
2. **Physical Uploads Directory**: [`adproject/uploads/`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/uploads/)
   - Contains all uploaded PDF documents referenced by `assessment_submissions`.

---

## 2. Steps to Set Up on Your Other Device

### Step 2.1: Clone/Pull Repository
After you push to GitHub from this device, clone or pull the repo on your new device:
```bash
git clone <your-repo-url>
cd AAS-Project-Main
```

### Step 2.2: Import the Database (`aas_db`)
Make sure MySQL is running on your new device, then run:

#### Using Command Prompt / PowerShell:
```powershell
mysql -u root -p < adproject/aas_db_dump.sql
```
*(Enter your MySQL root password when prompted)*

#### Or Using MySQL Workbench:
1. Open **MySQL Workbench**.
2. Connect to your local MySQL instance.
3. Go to **Server** &rarr; **Data Import**.
4. Choose **Import from Self-Contained File** and select `adproject/aas_db_dump.sql`.
5. Click **Start Import**.

---

### Step 2.3: Configure Database Credentials
Because `application-dev.properties` is in `.gitignore` to prevent leaking local credentials, create or edit `adproject/src/main/resources/application-dev.properties` on your new machine with your local MySQL password:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/aas_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=YOUR_MYSQL_PASSWORD_HERE
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
```

---

### Step 2.4: Run the Application
In your terminal:
```powershell
cd adproject
mvn clean spring-boot:run
```
Or run `AdprojectApplication.java` directly from VS Code / IntelliJ / Eclipse.

The system will start on `http://localhost:8080` with all your dummy data, assessments, student submissions, and assessor ink annotations fully intact!

