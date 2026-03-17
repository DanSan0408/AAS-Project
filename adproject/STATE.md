# Project State Documentation

## Project Overview
**Project Name**: adproject  
**Description**: Demo project for Spring Boot - AAS (Assessment Administration System)  
**Technology Stack**: Spring Boot 3.5.4, Java 17, MySQL, Thymeleaf  
**Package Structure**: `com.capstone.adproject`

## Dependencies
### Core Spring Boot Starters
- `spring-boot-starter-web` - Web MVC framework
- `spring-boot-starter-security` - Authentication and authorization
- `spring-boot-starter-data-jpa` - Database persistence
- `spring-boot-starter-thymeleaf` - Server-side templating
- `spring-boot-starter-validation` - Input validation
- `spring-boot-starter-mail` - Email functionality
- `spring-boot-starter-actuator` - Application monitoring

### Security & UI
- `thymeleaf-extras-springsecurity6` - Thymeleaf integration with Spring Security 6

### Database
- `mysql-connector-j` - MySQL database driver

### Development Tools
- `spring-boot-devtools` - Hot reload for development
- `lombok` - Code generation (getters, setters, constructors)

### Testing
- `spring-boot-starter-test` - Unit and integration testing
- `spring-security-test` - Security testing utilities

## Project Structure

### Source Code Organization (`src/main/java/com/capstone/adproject/`)

#### 1. **Application Entry Point**
- `AdprojectApplication.java` - Main Spring Boot application class

#### 2. **Configuration (`config/`)**
- `SecurityConfig.java` - Spring Security configuration
- `CustomAuthenticationSuccessHandler.java` - Custom login success handler
- `ThymeleafConfig.java` - Thymeleaf template configuration

#### 3. **Controllers (`controller/`)**
- `AdminController.java` - Admin-specific endpoints
- `AuthController.java` - Authentication and authorization endpoints
- `CommentConfigController.java` - Comment configuration management
- `DataViewController.java` - Data visualization endpoints
- `DeadlineController.java` - Deadline management
- `IndustrialSupervisorController.java` - Industrial supervisor functionality
- `LecturerAssessmentController.java` - Lecturer assessment management
- `LecturerController.java` - Lecturer-specific endpoints
- `LogoutController.java` - Logout handling
- `RubricController.java` - Rubric management
- `StudentController.java` - Student-specific endpoints
- `TemplateController.java` - Template management

#### 4. **Models (`model/`) - Domain Entities**
**User Management:**
- `Admin.java` - Administrator entity
- `Lecturer.java` - Lecturer entity
- `Student.java` - Student entity
- `IndustrialSupervisor.java` - Industrial supervisor entity

**Assessment System:**
- `Assessment.java` - Assessment entity
- `AssessmentComment.java` - Comments on assessments
- `Rubric.java` - Rubric definition
- `SubRubric.java` - Sub-components of rubrics
- `Rating.java` - Rating scale definitions
- `Mark.java` - Marks/score entity
- `StudentResultOverride.java` - Override for student results

**Group Management:**
- `Group.java` - Student groups
- `LecturerGroupAssignment.java` - Lecturer to group assignments


**Deadline Management:**
- `Deadline.java` - Deadline entity
- `DeadlineListWrapper.java` - Wrapper for deadline lists

#### 5. **Repositories (`repositories/`) - Data Access Layer**
JPA repositories for all domain entities including:
- `AdminRepository.java`, `LecturerRepository.java`, `StudentRepository.java`, `IndustrialSupervisorRepository.java`
- `AssessmentRepository.java`, `AssessmentCommentRepository.java`
- `RubricRepository.java`, `SubRubricRepository.java`, `RatingRepository.java`
- `GroupRepository.java`, `LecturerGroupAssignmentRepository.java`
- `MarkRepository.java`, `StudentResultOverrideRepository.java`
- `DeadlineRepository.java`, `RubricTemplateRepository.java`
- `UserRepository.java` - Generic user repository

#### 6. **Services (`service/`) - Business Logic Layer**
- `AdminService.java` - Admin business logic
- `AssessmentService.java` - Assessment management
- `AssessmentCommentService.java` - Comment management
- `CalculateService.java` - Calculation utilities
- `CustomUserDetailsService.java` - Custom user details for Spring Security
- `DeadlineService.java` - Deadline management
- `EmailService.java` - Email functionality
- `IndustrialSupervisorService.java` - Industrial supervisor operations
- `LecturerAssessmentService.java` - Lecturer assessment logic
- `MarkService.java` - Mark/score management
- `RubricService.java` - Rubric operations
- `StudentService.java` - Student management
- `UserService.java` - User management

#### 7. **DTOs (`dto/`) - Data Transfer Objects**
- `AssessmentAssignmentDto.java` - Assessment assignment data
- `AssessmentDataDto.java` - Assessment data transfer
- `GroupAssignmentDto.java` - Group assignment data
- `RandomizationInputDto.java` - Randomization input data
- `RubricCalculationDto.java` - Rubric calculation data
- `StudentAssessmentDataDto.java` - Student assessment data

#### 8. **Utilities (`util/`)**
- `PasswordGenerator.java` - Password generation utility

### Resources (`src/main/resources/`)

#### 1. **Templates (`templates/`) - Thymeleaf HTML Templates**
**Main Pages:**
- `login.html` - Login page
- `admin_home.html` - Admin dashboard
- `lecturer_home.html` - Lecturer dashboard
- `student_home.html` - Student dashboard
- `industrial_supervisor_home.html` - Supervisor dashboard

**Assessment Management:**
- `peer_assessment_form.html` - Peer assessment form
- `rubric-form.html` - Rubric creation/editing
- `view-assessment-rubrics.html` - View rubrics
- `lecturer_assessments.html` - Lecturer assessment view
- `student_assessments.html` - Student assessment view

**User Management:**
- `manage_users.html` - User management
- `manage_lecturers.html` - Lecturer management
- `manage_students.html` - Student management
- `manage_supervisors.html` - Supervisor management

**Group Management:**
- `group_assignment.html` - Group assignment
- `group_assignment_preview.html` - Group preview
- `edit_group.html` - Group editing
- `admin_assign_lecturers.html` - Lecturer assignment to groups

**Data Views:**
- `assessment_data_view.html` - Assessment data visualization
- `overall_data_view.html` - Overall data view
- `data_views_home.html` - Data views home page

**Configuration:**
- `comment_configuration_single_type.html` - Comment configuration
- `edit_single_comment.html` - Edit comments
- `edit-deadline.html` - Deadline editing
- `edit_overrides.html` - Result override editing

**Fragments (`templates/fragments/`):**
- Sidebar templates for different user roles

#### 2. **Static Assets (`static/`)**
- **CSS (`css/`)**: 40+ CSS files for styling different pages
- **JavaScript (`js/`)**: `loading.js` for loading animations
- **Images (`images/`)**: UTM logos and branding images

#### 3. **Configuration Files**
- `application.properties` - Spring Boot application configuration

## Key Features Identified

### 1. **Multi-role Authentication System**
- Admin, Lecturer, Student, Industrial Supervisor roles
- Spring Security with custom success handler
- Role-based access control

### 2. **Assessment Management System**
- Peer assessment functionality
- Rubric-based evaluation
- Template system for rubrics
- Comment configuration

### 3. **Group Management**
- Student group creation and assignment
- Lecturer assignment to groups
- Group-based assessment

### 4. **Data Visualization**
- Multiple data view templates
- Assessment data visualization
- Overall performance tracking

### 5. **Administrative Functions**
- User management for all roles
- Deadline management
- Result override capabilities
- Email functionality


## Technology Patterns

### Architecture
- **MVC Pattern**: Controllers, Services, Repositories
- **Layered Architecture**: Presentation → Business Logic → Data Access
- **DTO Pattern**: Separate data transfer objects

### Security
- Spring Security 6 integration
- Thymeleaf security extras
- Custom user details service

### Database
- JPA with MySQL
- Repository pattern
- Entity relationships

### Frontend
- Thymeleaf server-side rendering
- CSS styling per page/module
- Fragment-based layout (sidebars)

## Development Notes
- Uses Lombok for boilerplate code reduction
- Development tools enabled for hot reload
- Validation framework integrated
- Email service configured
- Actuator endpoints for monitoring

## Next Steps / Areas for Exploration
1. Database schema analysis (entity relationships)
2. Security configuration details
3. Business logic implementation in services
4. Template system implementation
5. Assessment calculation algorithms