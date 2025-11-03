
```
Capstone Project System 2 - Main
├─ adproject
│  ├─ .mvn
│  │  └─ wrapper
│  │     └─ maven-wrapper.properties
│  ├─ HELP.md
│  ├─ mvnw
│  ├─ mvnw.cmd
│  ├─ pom.xml
│  ├─ src
│  │  ├─ main
│  │  │  ├─ java
│  │  │  │  └─ com
│  │  │  │     └─ capstone
│  │  │  │        └─ adproject
│  │  │  │           ├─ AdprojectApplication.java
│  │  │  │           ├─ config
│  │  │  │           │  ├─ CustomAuthenticationSuccessHandler.java
│  │  │  │           │  ├─ SecurityConfig.java
│  │  │  │           │  └─ ThymeleafConfig.java
│  │  │  │           ├─ controller
│  │  │  │           │  ├─ AdminController.java
│  │  │  │           │  ├─ AuthController.java
│  │  │  │           │  ├─ CriteriaController.java
│  │  │  │           │  ├─ DeadlineController.java
│  │  │  │           │  ├─ IndustrialSupervisorController.java
│  │  │  │           │  ├─ LecturerController.java
│  │  │  │           │  ├─ LogoutController.java
│  │  │  │           │  ├─ RubricController.java
│  │  │  │           │  └─ StudentController.java
│  │  │  │           ├─ dto
│  │  │  │           │  ├─ GroupAssignmentDto.java
│  │  │  │           │  └─ RandomizationInputDto.java
│  │  │  │           ├─ model
│  │  │  │           │  ├─ Admin.java
│  │  │  │           │  ├─ Assessment.java
│  │  │  │           │  ├─ Criteria.java
│  │  │  │           │  ├─ CriteriaRating.java
│  │  │  │           │  ├─ Deadline.java
│  │  │  │           │  ├─ DeadlineListWrapper.java
│  │  │  │           │  ├─ Group.java
│  │  │  │           │  ├─ IndustrialSupervisor.java
│  │  │  │           │  ├─ Lecturer.java
│  │  │  │           │  ├─ Rating.java
│  │  │  │           │  ├─ Rubric.java
│  │  │  │           │  ├─ RubricCriteriaWrapper.java
│  │  │  │           │  ├─ Student.java
│  │  │  │           │  └─ SubRubric.java
│  │  │  │           ├─ repositories
│  │  │  │           │  ├─ AdminRepository.java
│  │  │  │           │  ├─ AssessmentRepository.java
│  │  │  │           │  ├─ CriteriaRepository.java
│  │  │  │           │  ├─ DeadlineRepository.java
│  │  │  │           │  ├─ GroupRepository.java
│  │  │  │           │  ├─ IndustrialSupervisorRepository.java
│  │  │  │           │  ├─ LecturerRepository.java
│  │  │  │           │  ├─ RatingRepository.java
│  │  │  │           │  ├─ RubricRepository.java
│  │  │  │           │  ├─ StudentRepository.java
│  │  │  │           │  ├─ SubRubricRepository.java
│  │  │  │           │  └─ UserRepository.java
│  │  │  │           └─ service
│  │  │  │              ├─ AdminService.java
│  │  │  │              ├─ AssessmentService.java
│  │  │  │              ├─ CriteriaService.java
│  │  │  │              ├─ CustomUserDetailsService.java
│  │  │  │              ├─ DeadlineService.java
│  │  │  │              ├─ EmailService.java
│  │  │  │              ├─ RubricService.java
│  │  │  │              └─ UserService.java
│  │  │  └─ resources
│  │  │     ├─ application.properties
│  │  │     ├─ static
│  │  │     │  ├─ css
│  │  │     │  │  ├─ login.css
│  │  │     │  │  ├─ manageUser.css
│  │  │     │  │  ├─ rubric.css
│  │  │     │  │  └─ style.css
│  │  │     │  └─ images
│  │  │     │     ├─ bulat_cas.png
│  │  │     │     └─ utmlogo.png
│  │  │     └─ templates
│  │  │        ├─ admin_home.html
│  │  │        ├─ criteria-form.html
│  │  │        ├─ edit-deadline.html
│  │  │        ├─ edit_group.html
│  │  │        ├─ forgot_password_form.html
│  │  │        ├─ fragments
│  │  │        │  ├─ common_sidebar.html
│  │  │        │  └─ sidebar.html
│  │  │        ├─ group_assignment.html
│  │  │        ├─ group_assignment_preview.html
│  │  │        ├─ industrial_supervisor_home.html
│  │  │        ├─ lecturer_home.html
│  │  │        ├─ login.html
│  │  │        ├─ manage-assessments.html
│  │  │        ├─ manage_lecturers.html
│  │  │        ├─ manage_students.html
│  │  │        ├─ manage_supervisors.html
│  │  │        ├─ manage_users.html
│  │  │        ├─ reset_password_form.html
│  │  │        ├─ rubric-form.html
│  │  │        ├─ rubric_management.html
│  │  │        ├─ startup.html
│  │  │        ├─ student_home.html
│  │  │        └─ view-assessment-rubrics.html
│  │  └─ test
│  │     └─ java
│  │        └─ com
│  │           └─ capstone
│  │              └─ adproject
│  │                 └─ AdprojectApplicationTests.java
│  └─ target
│     ├─ adproject-0.0.1-SNAPSHOT.jar
│     ├─ adproject-0.0.1-SNAPSHOT.jar.original
│     ├─ classes
│     │  ├─ application.properties
│     │  ├─ com
│     │  │  └─ capstone
│     │  │     └─ adproject
│     │  │        ├─ AdprojectApplication.class
│     │  │        ├─ config
│     │  │        │  ├─ CustomAuthenticationSuccessHandler.class
│     │  │        │  ├─ SecurityConfig.class
│     │  │        │  └─ ThymeleafConfig.class
│     │  │        ├─ controller
│     │  │        │  ├─ AdminController.class
│     │  │        │  ├─ AuthController.class
│     │  │        │  ├─ CriteriaController.class
│     │  │        │  ├─ DeadlineController.class
│     │  │        │  ├─ IndustrialSupervisorController.class
│     │  │        │  ├─ LecturerController.class
│     │  │        │  ├─ LogoutController.class
│     │  │        │  ├─ RubricController.class
│     │  │        │  └─ StudentController.class
│     │  │        ├─ dto
│     │  │        │  ├─ GroupAssignmentDto.class
│     │  │        │  └─ RandomizationInputDto.class
│     │  │        ├─ model
│     │  │        │  ├─ Admin.class
│     │  │        │  ├─ Assessment.class
│     │  │        │  ├─ Criteria.class
│     │  │        │  ├─ CriteriaRating.class
│     │  │        │  ├─ Deadline.class
│     │  │        │  ├─ DeadlineListWrapper.class
│     │  │        │  ├─ Group.class
│     │  │        │  ├─ IndustrialSupervisor.class
│     │  │        │  ├─ Lecturer.class
│     │  │        │  ├─ Rating.class
│     │  │        │  ├─ Rubric.class
│     │  │        │  ├─ RubricCriteriaWrapper.class
│     │  │        │  ├─ Student.class
│     │  │        │  └─ SubRubric.class
│     │  │        ├─ repositories
│     │  │        │  ├─ AdminRepository.class
│     │  │        │  ├─ AssessmentRepository.class
│     │  │        │  ├─ CriteriaRepository.class
│     │  │        │  ├─ DeadlineRepository.class
│     │  │        │  ├─ GroupRepository.class
│     │  │        │  ├─ IndustrialSupervisorRepository.class
│     │  │        │  ├─ LecturerRepository.class
│     │  │        │  ├─ RatingRepository.class
│     │  │        │  ├─ RubricRepository.class
│     │  │        │  ├─ StudentRepository.class
│     │  │        │  ├─ SubRubricRepository.class
│     │  │        │  └─ UserRepository.class
│     │  │        └─ service
│     │  │           ├─ AdminService.class
│     │  │           ├─ AssessmentService.class
│     │  │           ├─ CriteriaService.class
│     │  │           ├─ CustomUserDetailsService.class
│     │  │           ├─ DeadlineService.class
│     │  │           ├─ EmailService.class
│     │  │           ├─ RubricService.class
│     │  │           └─ UserService.class
│     │  ├─ static
│     │  │  ├─ css
│     │  │  │  ├─ login.css
│     │  │  │  ├─ manageUser.css
│     │  │  │  ├─ rubric.css
│     │  │  │  └─ style.css
│     │  │  └─ images
│     │  │     ├─ bulat_cas.png
│     │  │     └─ utmlogo.png
│     │  └─ templates
│     │     ├─ admin_home.html
│     │     ├─ criteria-form.html
│     │     ├─ edit-deadline.html
│     │     ├─ edit_group.html
│     │     ├─ forgot_password_form.html
│     │     ├─ fragments
│     │     │  ├─ common_sidebar.html
│     │     │  └─ sidebar.html
│     │     ├─ group_assignment.html
│     │     ├─ group_assignment_preview.html
│     │     ├─ industrial_supervisor_home.html
│     │     ├─ lecturer_home.html
│     │     ├─ login.html
│     │     ├─ manage-assessments.html
│     │     ├─ manage_lecturers.html
│     │     ├─ manage_students.html
│     │     ├─ manage_supervisors.html
│     │     ├─ manage_users.html
│     │     ├─ reset_password_form.html
│     │     ├─ rubric-form.html
│     │     ├─ rubric_management.html
│     │     ├─ startup.html
│     │     ├─ student_home.html
│     │     └─ view-assessment-rubrics.html
│     ├─ generated-sources
│     │  └─ annotations
│     ├─ generated-test-sources
│     │  └─ test-annotations
│     ├─ maven-archiver
│     │  └─ pom.properties
│     ├─ maven-status
│     │  └─ maven-compiler-plugin
│     │     ├─ compile
│     │     │  └─ default-compile
│     │     │     ├─ createdFiles.lst
│     │     │     └─ inputFiles.lst
│     │     └─ testCompile
│     │        └─ default-testCompile
│     │           ├─ createdFiles.lst
│     │           └─ inputFiles.lst
│     ├─ surefire-reports
│     │  ├─ com.capstone.adproject.AdprojectApplicationTests.txt
│     │  └─ TEST-com.capstone.adproject.AdprojectApplicationTests.xml
│     └─ test-classes
│        └─ com
│           └─ capstone
│              └─ adproject
│                 └─ AdprojectApplicationTests.class
└─ SQLQuery1.sql

```