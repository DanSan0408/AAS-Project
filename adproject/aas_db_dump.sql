-- MySQL dump 10.13  Distrib 9.4.0, for Win64 (x86_64)
--
-- Host: localhost    Database: aas_db
-- ------------------------------------------------------
-- Server version	9.4.0

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `aas_db`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `aas_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `aas_db`;

--
-- Table structure for table `admin`
--

DROP TABLE IF EXISTS `admin`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `email` varchar(255) NOT NULL,
  `password` varchar(255) NOT NULL,
  `resetPasswordToken` varchar(255) DEFAULT NULL,
  `username` varchar(255) NOT NULL,
  `resetpasswordtokenexpiry` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKc0r9atamxvbhjjvy5j8da1kam` (`email`),
  UNIQUE KEY `UKgfn44sntic2k93auag97juyij` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `admin`
--

LOCK TABLES `admin` WRITE;
/*!40000 ALTER TABLE `admin` DISABLE KEYS */;
INSERT INTO `admin` VALUES (3,'admin@gmail.com','$2a$12$wyaKf/46FdVTnYk372rKdeAexlH45qodQpCfySV2rmVRhPeZC7i/G',NULL,'Admin ',NULL),(4,'liew@gmail.com','$2a$12$wyaKf/46FdVTnYk372rKdeAexlH45qodQpCfySV2rmVRhPeZC7i/G',NULL,'Dr. Liew',NULL),(5,'ihsan.rubix@gmail.com','$2a$10$UQZw/A73aK5E9d3YDh6BXOPbAmdOshSKedvj1dxniZElwQmZBL4Zy',NULL,'Ihsan',NULL),(6,'adamanas@gmail.com','$2a$10$4l6eENZmKd4ekAiCSZoGjOJG41fq25iQnNHXd9vHL2M3GEf4.rGDS',NULL,'Adam',NULL);
/*!40000 ALTER TABLE `admin` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `admin_course_assignment`
--

DROP TABLE IF EXISTS `admin_course_assignment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_course_assignment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_id` bigint NOT NULL,
  `lecturer_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKjt91ah7ta7364ovlos81k9mmu` (`lecturer_id`),
  KEY `FKp4b7s72j7l867e1p4eh87sgka` (`course_id`),
  CONSTRAINT `FKjt91ah7ta7364ovlos81k9mmu` FOREIGN KEY (`lecturer_id`) REFERENCES `lecturer` (`id`),
  CONSTRAINT `FKp4b7s72j7l867e1p4eh87sgka` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `admin_course_assignment`
--

LOCK TABLES `admin_course_assignment` WRITE;
/*!40000 ALTER TABLE `admin_course_assignment` DISABLE KEYS */;
INSERT INTO `admin_course_assignment` VALUES (14,16,8),(15,17,9),(16,16,9),(17,18,13);
/*!40000 ALTER TABLE `admin_course_assignment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `assessment`
--

DROP TABLE IF EXISTS `assessment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `assessment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(255) DEFAULT NULL,
  `assessment_type` varchar(255) DEFAULT NULL,
  `evaluation_type` varchar(255) DEFAULT NULL,
  `course_id` bigint DEFAULT NULL,
  `isPeerAssessment` bit(1) DEFAULT NULL,
  `isSelfAssessment` bit(1) DEFAULT NULL,
  `comment_count` int DEFAULT NULL,
  `comment_min_length` int DEFAULT NULL,
  `comments_anonymous` bit(1) DEFAULT NULL,
  `group_comment_count` int DEFAULT NULL,
  `group_comment_min_length` int DEFAULT NULL,
  `group_comments_anonymous` bit(1) DEFAULT NULL,
  `individual_comment_count` int DEFAULT NULL,
  `individual_comment_min_length` int DEFAULT NULL,
  `individual_comments_anonymous` bit(1) DEFAULT NULL,
  `group_order` int DEFAULT NULL,
  `individual_order` int DEFAULT NULL,
  `group_comment_anonymous_flags` text,
  `group_comment_min_lengths` text,
  `individual_comment_anonymous_flags` text,
  `individual_comment_min_lengths` text,
  `is_template` tinyint(1) NOT NULL DEFAULT '0',
  `extra_notes` text,
  `extra_notes_for_student` text,
  `factor_contribution_type` varchar(255) DEFAULT NULL,
  `max_file_size_mb` int DEFAULT NULL,
  `requires_submission` bit(1) DEFAULT NULL,
  `submission_instructions` text,
  `submission_scope` varchar(20) DEFAULT NULL,
  `submission_title` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK8egag0ypake0n45mkp3geafod` (`course_id`),
  KEY `idx_assessment_course` (`course_id`),
  CONSTRAINT `FK383xcrx1r57hd8oiwbod4aoc4` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=57 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `assessment`
--

LOCK TABLES `assessment` WRITE;
/*!40000 ALTER TABLE `assessment` DISABLE KEYS */;
INSERT INTO `assessment` VALUES (52,'test assessment 1',NULL,NULL,16,NULL,NULL,NULL,NULL,NULL,0,20,_binary '',1,20,_binary '',1,0,NULL,NULL,'[true]','[20]',0,'https://music.youtube.com/playlist?list&#61;PLKm6hRS9RvJBdRRwWW-hIGcA08SMpcBs1',NULL,NULL,50,_binary '','Submit Things','INDIVIDUAL','Final Year Project'),(53,'test assessment 2',NULL,NULL,16,NULL,NULL,NULL,NULL,NULL,1,20,_binary '',0,20,_binary '',1,0,'[true]','[20]',NULL,NULL,0,NULL,'test extra notes for students',NULL,NULL,NULL,NULL,NULL,NULL),(55,'test assessment 3',NULL,NULL,16,NULL,NULL,NULL,NULL,NULL,0,20,_binary '',0,20,_binary '',1,0,NULL,NULL,NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL),(56,'test assessment 4',NULL,NULL,16,NULL,NULL,NULL,NULL,NULL,0,20,_binary '',0,20,_binary '',1,0,NULL,NULL,NULL,NULL,0,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL);
/*!40000 ALTER TABLE `assessment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `assessment_comments`
--

DROP TABLE IF EXISTS `assessment_comments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `assessment_comments` (
  `comment_index` int DEFAULT NULL,
  `assessment_id` bigint NOT NULL,
  `evaluated_student_id` bigint NOT NULL,
  `evaluator_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rubric_id` bigint DEFAULT NULL,
  `rubric_assessment_type` varchar(50) DEFAULT NULL,
  `anonymous_identifier` varchar(255) DEFAULT NULL,
  `commentText` text NOT NULL,
  `comment_label` text,
  `evaluator_name` varchar(255) DEFAULT NULL,
  `assessmentType` enum('LECTURER_EVALUATION','PEER','SELF','SUPERVISOR_EVALUATION','TEAM') NOT NULL,
  `evaluatorType` enum('LECTURER','STUDENT','SUPERVISOR') NOT NULL,
  `submitted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `approval_status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `approved_at` datetime(6) DEFAULT NULL,
  `approved_by` varchar(255) DEFAULT NULL,
  `flag_reason` text,
  `is_approved` tinyint(1) NOT NULL DEFAULT '0',
  `is_flagged_negative` tinyint(1) NOT NULL DEFAULT '0',
  `sentiment_score` double DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK7fu2aaquk3lmmvntjw0bygbg4` (`assessment_id`),
  KEY `FKj1576c9pjci9gj3m6fmi3sy5o` (`evaluated_student_id`),
  CONSTRAINT `FK7fu2aaquk3lmmvntjw0bygbg4` FOREIGN KEY (`assessment_id`) REFERENCES `assessment` (`id`),
  CONSTRAINT `FKj1576c9pjci9gj3m6fmi3sy5o` FOREIGN KEY (`evaluated_student_id`) REFERENCES `student` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `assessment_comments`
--

LOCK TABLES `assessment_comments` WRITE;
/*!40000 ALTER TABLE `assessment_comments` DISABLE KEYS */;
INSERT INTO `assessment_comments` VALUES (0,52,96,8,18,NULL,'Individual Assessment','Lecturer','vszxzcdsfdsfdsfsfdsfsffsvvsfdsfsd','test question two admin','admin@gmail.com','LECTURER_EVALUATION','LECTURER','2026-08-18 01:28:03','PENDING',NULL,NULL,'Manually flagged by administrator',0,1,NULL),(0,52,97,8,20,NULL,'Individual Assessment','Lecturer','A very good and cooperative student','test question two admin','admin@gmail.com','LECTURER_EVALUATION','LECTURER','2026-08-18 01:28:03','PENDING',NULL,NULL,NULL,0,0,NULL),(0,52,95,8,21,NULL,'Individual Assessment','Assessor','ffdfadsfdsfsdfsdfsdgsgdsgds','test question two admin','admin@gmail.com','LECTURER_EVALUATION','LECTURER','2026-10-27 17:13:00','PENDING',NULL,NULL,NULL,0,0,0);
/*!40000 ALTER TABLE `assessment_comments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `assessment_groupcommentlabels`
--

DROP TABLE IF EXISTS `assessment_groupcommentlabels`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `assessment_groupcommentlabels` (
  `Assessment_id` bigint NOT NULL,
  `label` varchar(500) DEFAULT NULL,
  KEY `FKbsn61690ls8tbh3xk9wbih843` (`Assessment_id`),
  CONSTRAINT `FKbsn61690ls8tbh3xk9wbih843` FOREIGN KEY (`Assessment_id`) REFERENCES `assessment` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `assessment_groupcommentlabels`
--

LOCK TABLES `assessment_groupcommentlabels` WRITE;
/*!40000 ALTER TABLE `assessment_groupcommentlabels` DISABLE KEYS */;
INSERT INTO `assessment_groupcommentlabels` VALUES (48,'nhbasdjhbfajkshfjdkajkafjkajkafbjkfdbajk'),(50,'sfdssdfsdfdxzdssdfsdfdsfsd'),(53,'bakdnhsakhfdakwhfkahsklfbnaskl');
/*!40000 ALTER TABLE `assessment_groupcommentlabels` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `assessment_individualcommentlabels`
--

DROP TABLE IF EXISTS `assessment_individualcommentlabels`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `assessment_individualcommentlabels` (
  `Assessment_id` bigint NOT NULL,
  `label` varchar(500) DEFAULT NULL,
  KEY `FKi2ebhalngq4er1uabm01vpmmp` (`Assessment_id`),
  CONSTRAINT `FKi2ebhalngq4er1uabm01vpmmp` FOREIGN KEY (`Assessment_id`) REFERENCES `assessment` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `assessment_individualcommentlabels`
--

LOCK TABLES `assessment_individualcommentlabels` WRITE;
/*!40000 ALTER TABLE `assessment_individualcommentlabels` DISABLE KEYS */;
INSERT INTO `assessment_individualcommentlabels` VALUES (48,'erewrewrwerwerwerwer'),(52,'test question two admin');
/*!40000 ALTER TABLE `assessment_individualcommentlabels` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `assessment_submissions`
--

DROP TABLE IF EXISTS `assessment_submissions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `assessment_submissions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content_type` varchar(100) DEFAULT NULL,
  `file_size_bytes` bigint NOT NULL,
  `original_file_name` varchar(255) NOT NULL,
  `status` varchar(20) DEFAULT NULL,
  `stored_file_name` varchar(255) NOT NULL,
  `submitted_at` datetime(6) NOT NULL,
  `version` int DEFAULT NULL,
  `assessment_id` bigint NOT NULL,
  `course_id` bigint NOT NULL,
  `group_id` bigint DEFAULT NULL,
  `student_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_submission_assessment` (`assessment_id`),
  KEY `idx_submission_course` (`course_id`),
  KEY `idx_submission_group` (`group_id`),
  KEY `idx_submission_student` (`student_id`),
  CONSTRAINT `FK5a5tu24ooo3rm0ljl9svts7gt` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`),
  CONSTRAINT `FK8y2qd6bnlay8cq0acjwtafu1y` FOREIGN KEY (`group_id`) REFERENCES `project_group` (`id`),
  CONSTRAINT `FKgdp85xpw5d35gyxnnea2pdh0y` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`),
  CONSTRAINT `FKtfrsro8em7706vksqsms7rjwl` FOREIGN KEY (`assessment_id`) REFERENCES `assessment` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `assessment_submissions`
--

LOCK TABLES `assessment_submissions` WRITE;
/*!40000 ALTER TABLE `assessment_submissions` DISABLE KEYS */;
INSERT INTO `assessment_submissions` VALUES (1,'application/pdf',817805,'Science Lab Report Print.pdf','SUBMITTED','course_16/assessment_52/student_95/sub_1793116579775_1445985a.pdf','2026-10-27 15:56:19.804619',1,52,16,NULL,95);
/*!40000 ALTER TABLE `assessment_submissions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `calculated_results`
--

DROP TABLE IF EXISTS `calculated_results`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `calculated_results` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `assessment_type` varchar(255) DEFAULT NULL,
  `calculated_at` datetime(6) DEFAULT NULL,
  `calculation_type` varchar(255) DEFAULT NULL,
  `clo` int DEFAULT NULL,
  `clo_total_marks` text,
  `clo_weighted_marks` text,
  `configured_rubric_mark` double DEFAULT NULL,
  `evaluated_rubric_mark` double DEFAULT NULL,
  `final_total_marks` double DEFAULT NULL,
  `rubric_factor` double DEFAULT NULL,
  `student_factor` double DEFAULT NULL,
  `total_group_marks` double DEFAULT NULL,
  `total_individual_marks` double DEFAULT NULL,
  `weighted_rubric_mark` double DEFAULT NULL,
  `assessment_id` bigint NOT NULL,
  `rubric_id` bigint DEFAULT NULL,
  `student_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5qj9bh94hkh5yp73dmwvdvs4x` (`assessment_id`),
  KEY `FKilsrm0r8h0goo1n50e1n2e86f` (`rubric_id`),
  KEY `FKdfm4c2eqpgxf1sjxib92o5ood` (`student_id`),
  CONSTRAINT `FK5qj9bh94hkh5yp73dmwvdvs4x` FOREIGN KEY (`assessment_id`) REFERENCES `assessment` (`id`),
  CONSTRAINT `FKdfm4c2eqpgxf1sjxib92o5ood` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKilsrm0r8h0goo1n50e1n2e86f` FOREIGN KEY (`rubric_id`) REFERENCES `rubric` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=334 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `calculated_results`
--

LOCK TABLES `calculated_results` WRITE;
/*!40000 ALTER TABLE `calculated_results` DISABLE KEYS */;
/*!40000 ALTER TABLE `calculated_results` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `courses`
--

DROP TABLE IF EXISTS `courses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `courses` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `courseCode` varchar(255) DEFAULT NULL,
  `courseName` varchar(255) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `created_by_lecturer_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_course_created_by` (`created_by_lecturer_id`),
  CONSTRAINT `fk_course_created_by` FOREIGN KEY (`created_by_lecturer_id`) REFERENCES `lecturer` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `courses`
--

LOCK TABLES `courses` WRITE;
/*!40000 ALTER TABLE `courses` DISABLE KEYS */;
INSERT INTO `courses` VALUES (16,'C01','test course 1','etc',8),(17,'C02','test course 2','etc',9),(18,'C03','test course 3','etc',13);
/*!40000 ALTER TABLE `courses` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `deadlines`
--

DROP TABLE IF EXISTS `deadlines`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `deadlines` (
  `assessmentId` bigint DEFAULT NULL,
  `date` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `openDate` datetime(6) DEFAULT NULL,
  `assessorType` varchar(255) DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  `courseId` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_deadline_assessment_assessor` (`assessmentId`,`assessorType`),
  KEY `idx_deadline_course` (`courseId`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `deadlines`
--

LOCK TABLES `deadlines` WRITE;
/*!40000 ALTER TABLE `deadlines` DISABLE KEYS */;
INSERT INTO `deadlines` VALUES (53,'2026-08-01 00:00:00.000000',12,'2026-06-19 01:35:47.719000','STUDENT','test assessment 2 - Assignment',16),(52,'2026-10-31 15:59:00.000000',17,'2026-10-27 15:20:22.664000','GENERAL','test assessment 1 - Assignment',16),(55,'2026-07-24 23:59:00.000000',18,'2026-07-03 03:56:19.452000','GENERAL','test assessment 3 - Assignment',16),(56,'2026-07-31 23:59:00.000000',19,'2026-07-25 12:43:37.811000','GENERAL','test assessment 4 - Assignment',16);
/*!40000 ALTER TABLE `deadlines` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `factor_override_history`
--

DROP TABLE IF EXISTS `factor_override_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `factor_override_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `changedAt` datetime(6) DEFAULT NULL,
  `changedBy` varchar(255) DEFAULT NULL,
  `newFactor` double DEFAULT NULL,
  `newGrandTotal` double DEFAULT NULL,
  `oldFactor` double DEFAULT NULL,
  `oldGrandTotal` double DEFAULT NULL,
  `student_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_factor_override_history_student` (`student_id`),
  CONSTRAINT `fk_factor_override_history_student` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `factor_override_history`
--

LOCK TABLES `factor_override_history` WRITE;
/*!40000 ALTER TABLE `factor_override_history` DISABLE KEYS */;
INSERT INTO `factor_override_history` VALUES (1,'2026-08-18 00:08:00.547984','admin@gmail.com',1,NULL,NULL,NULL,96),(2,'2026-08-23 19:17:08.279753','admin@gmail.com',0.7,NULL,1,NULL,96);
/*!40000 ALTER TABLE `factor_override_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `factor_weightages`
--

DROP TABLE IF EXISTS `factor_weightages`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `factor_weightages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `weightage` double DEFAULT NULL,
  `assessment_id` bigint NOT NULL,
  `course_id` bigint NOT NULL,
  `factor_contribution_type` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_factor_weightage_assessment` (`assessment_id`),
  KEY `fk_factor_weightage_course` (`course_id`),
  CONSTRAINT `fk_factor_weightage_assessment` FOREIGN KEY (`assessment_id`) REFERENCES `assessment` (`id`),
  CONSTRAINT `fk_factor_weightage_course` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `factor_weightages`
--

LOCK TABLES `factor_weightages` WRITE;
/*!40000 ALTER TABLE `factor_weightages` DISABLE KEYS */;
INSERT INTO `factor_weightages` VALUES (7,20,53,16,'GROUP'),(8,80,55,16,'BOTH');
/*!40000 ALTER TABLE `factor_weightages` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `industrial_supervisor`
--

DROP TABLE IF EXISTS `industrial_supervisor`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `industrial_supervisor` (
  `isTempPassword` tinyint(1) NOT NULL DEFAULT '0',
  `id` bigint NOT NULL AUTO_INCREMENT,
  `email` varchar(255) NOT NULL,
  `password` varchar(255) NOT NULL,
  `resetPasswordToken` varchar(255) DEFAULT NULL,
  `username` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKk96wib96iw5rwp2twwc8s3hp6` (`email`),
  UNIQUE KEY `UKjl0wos85kctoksbmprkc5tts9` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `industrial_supervisor`
--

LOCK TABLES `industrial_supervisor` WRITE;
/*!40000 ALTER TABLE `industrial_supervisor` DISABLE KEYS */;
/*!40000 ALTER TABLE `industrial_supervisor` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `lecturer`
--

DROP TABLE IF EXISTS `lecturer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lecturer` (
  `isTempPassword` tinyint(1) NOT NULL DEFAULT '0',
  `course_id` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `email` varchar(255) NOT NULL,
  `password` varchar(255) NOT NULL,
  `resetPasswordToken` varchar(255) DEFAULT NULL,
  `roles` varchar(255) DEFAULT NULL,
  `username` varchar(255) DEFAULT NULL,
  `resetpasswordtokenexpiry` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK4t2335niv9bys5fka8redx6k1` (`username`),
  UNIQUE KEY `idx_lecturer_username` (`username`),
  UNIQUE KEY `uk_lecturer_email_course` (`email`,`course_id`),
  UNIQUE KEY `idx_lecturer_email_course` (`email`,`course_id`),
  KEY `idx_lecturer_course` (`course_id`),
  CONSTRAINT `FKa5kcsfysg3irth21csju5fe7f` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `lecturer`
--

LOCK TABLES `lecturer` WRITE;
/*!40000 ALTER TABLE `lecturer` DISABLE KEYS */;
INSERT INTO `lecturer` VALUES (0,16,8,'admin@gmail.com','',NULL,'ROLE_ADMIN,ROLE_LECTURER','admin power',NULL),(0,16,9,'liew@gmail.com','',NULL,'ROLE_ADMIN,ROLE_LECTURER','liew',NULL),(0,18,13,'ihsan.rubix@gmail.com','$2a$12$ssFqUcCwwdaQSCaDc.21mOJJTXnTd2kro18e8/AjMCaiZyjrCn./e',NULL,'ROLE_ADMIN,ROLE_LECTURER','superadmin',NULL),(1,16,14,'lecturer1@gmail.com','$2a$12$T7IWA2k7TU7qgZGaBPBeF.MhnjJNz0XCubNzbVm5GA.BisTltMALO','df535193-3914-45ca-b724-113cf25aef02','ROLE_LECTURER','Lecturer 1',NULL);
/*!40000 ALTER TABLE `lecturer` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `lecturer_group_assignment`
--

DROP TABLE IF EXISTS `lecturer_group_assignment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lecturer_group_assignment` (
  `assessment_id` bigint NOT NULL,
  `group_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `lecturer_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6662kggatimw8bfr77kq973xg` (`group_id`),
  KEY `FK7hlwfe8ebu3sjxkoysw5pnfei` (`lecturer_id`),
  KEY `idx_lga_assessment_group` (`assessment_id`,`group_id`),
  KEY `idx_lga_assessment_lecturer` (`assessment_id`,`lecturer_id`),
  CONSTRAINT `FK6662kggatimw8bfr77kq973xg` FOREIGN KEY (`group_id`) REFERENCES `project_group` (`id`),
  CONSTRAINT `FK7hlwfe8ebu3sjxkoysw5pnfei` FOREIGN KEY (`lecturer_id`) REFERENCES `lecturer` (`id`),
  CONSTRAINT `FKdhbehwk2qecqyq4ncqjn3tmy6` FOREIGN KEY (`assessment_id`) REFERENCES `assessment` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `lecturer_group_assignment`
--

LOCK TABLES `lecturer_group_assignment` WRITE;
/*!40000 ALTER TABLE `lecturer_group_assignment` DISABLE KEYS */;
INSERT INTO `lecturer_group_assignment` VALUES (55,11,7,8),(55,13,8,8);
/*!40000 ALTER TABLE `lecturer_group_assignment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `lecturer_rubric_assignments`
--

DROP TABLE IF EXISTS `lecturer_rubric_assignments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lecturer_rubric_assignments` (
  `assessment_id` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `lecturer_id` bigint DEFAULT NULL,
  `rubric_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKa3lsww66ec1seqtp0u9a7ajl3` (`lecturer_id`),
  KEY `FKrv6qssrovvsewcj5m3w4giovu` (`rubric_id`),
  KEY `idx_lra_assessment_rubric` (`assessment_id`,`rubric_id`),
  KEY `idx_lra_assessment_lecturer` (`assessment_id`,`lecturer_id`),
  CONSTRAINT `FKa3lsww66ec1seqtp0u9a7ajl3` FOREIGN KEY (`lecturer_id`) REFERENCES `lecturer` (`id`),
  CONSTRAINT `FKrmkov9rfkh6teavns8tkqggwm` FOREIGN KEY (`assessment_id`) REFERENCES `assessment` (`id`),
  CONSTRAINT `FKrv6qssrovvsewcj5m3w4giovu` FOREIGN KEY (`rubric_id`) REFERENCES `rubric` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `lecturer_rubric_assignments`
--

LOCK TABLES `lecturer_rubric_assignments` WRITE;
/*!40000 ALTER TABLE `lecturer_rubric_assignments` DISABLE KEYS */;
INSERT INTO `lecturer_rubric_assignments` VALUES (56,3,8,297);
/*!40000 ALTER TABLE `lecturer_rubric_assignments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `lecturer_student_assignment`
--

DROP TABLE IF EXISTS `lecturer_student_assignment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lecturer_student_assignment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `assessment_id` bigint NOT NULL,
  `lecturer_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_lsa_assessment_student` (`assessment_id`,`student_id`),
  KEY `idx_lsa_assessment_lecturer` (`assessment_id`,`lecturer_id`),
  KEY `FKm2mekdnysf8wn19aoxus65077` (`lecturer_id`),
  KEY `FKhvcbrfd3i6a32ue2gos88cr3j` (`student_id`),
  CONSTRAINT `FKdlr1n94oxdtgg7uqoba68xjvq` FOREIGN KEY (`assessment_id`) REFERENCES `assessment` (`id`),
  CONSTRAINT `FKhvcbrfd3i6a32ue2gos88cr3j` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKm2mekdnysf8wn19aoxus65077` FOREIGN KEY (`lecturer_id`) REFERENCES `lecturer` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `lecturer_student_assignment`
--

LOCK TABLES `lecturer_student_assignment` WRITE;
/*!40000 ALTER TABLE `lecturer_student_assignment` DISABLE KEYS */;
INSERT INTO `lecturer_student_assignment` VALUES (9,52,8,96),(10,52,8,97),(11,52,8,98),(12,52,8,95);
/*!40000 ALTER TABLE `lecturer_student_assignment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `marks`
--

DROP TABLE IF EXISTS `marks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `marks` (
  `clo` int NOT NULL,
  `cloMarks` double NOT NULL,
  `is_supervisor_mark` bit(1) DEFAULT NULL,
  `markValue` double NOT NULL,
  `assessment_id` bigint NOT NULL,
  `evaluated_student_id` bigint NOT NULL,
  `evaluator_student_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `lecturer_id` bigint DEFAULT NULL,
  `rating_id` bigint DEFAULT NULL,
  `rubric_id` bigint DEFAULT NULL,
  `sub_rubric_id` bigint DEFAULT NULL,
  `supervisor_id` bigint DEFAULT NULL,
  `assessment_type` varchar(255) NOT NULL,
  `comments` text,
  `status` enum('DRAFT','FINAL','SUBMITTED') NOT NULL,
  `submitted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `FKt13uojipfm655xsu6bfhr1yha` (`assessment_id`),
  KEY `FK1bpgc94o9q28ae4168qcy30nt` (`lecturer_id`),
  KEY `FKcq91fe82idwj4rir4kb89at4o` (`rating_id`),
  KEY `FK2ewy79hy8vd408bt6w41kof6p` (`rubric_id`),
  KEY `FKntqihpaw95f03yip6uum9li2h` (`sub_rubric_id`),
  KEY `FK5r9f341t3mm4r9xk0n0jn4iwb` (`evaluated_student_id`),
  KEY `FKifd9c803fdkrn18egv6998smm` (`evaluator_student_id`),
  CONSTRAINT `FK1bpgc94o9q28ae4168qcy30nt` FOREIGN KEY (`lecturer_id`) REFERENCES `lecturer` (`id`),
  CONSTRAINT `FK2ewy79hy8vd408bt6w41kof6p` FOREIGN KEY (`rubric_id`) REFERENCES `rubric` (`id`),
  CONSTRAINT `FK5r9f341t3mm4r9xk0n0jn4iwb` FOREIGN KEY (`evaluated_student_id`) REFERENCES `student` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKcq91fe82idwj4rir4kb89at4o` FOREIGN KEY (`rating_id`) REFERENCES `rating` (`id`),
  CONSTRAINT `FKifd9c803fdkrn18egv6998smm` FOREIGN KEY (`evaluator_student_id`) REFERENCES `student` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKntqihpaw95f03yip6uum9li2h` FOREIGN KEY (`sub_rubric_id`) REFERENCES `sub_rubric` (`id`),
  CONSTRAINT `FKt13uojipfm655xsu6bfhr1yha` FOREIGN KEY (`assessment_id`) REFERENCES `assessment` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=91 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `marks`
--

LOCK TABLES `marks` WRITE;
/*!40000 ALTER TABLE `marks` DISABLE KEYS */;
INSERT INTO `marks` VALUES (2,1,_binary '\0',1,52,96,96,21,8,32,291,4,NULL,'Group Assessment','LECTURER:8','FINAL','2026-08-18 01:28:03'),(2,1,_binary '\0',1,52,96,96,22,8,27,290,NULL,NULL,'Individual Assessment','LECTURER:8','FINAL','2026-08-18 01:28:03'),(3,2,_binary '\0',2,53,96,95,23,NULL,41,293,6,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,3,_binary '\0',3,53,96,95,24,NULL,47,294,NULL,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,2,_binary '\0',2,53,95,95,25,NULL,41,293,6,NULL,'Self Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,2,_binary '\0',2,53,95,95,26,NULL,46,294,NULL,NULL,'Self Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(2,0,_binary '\0',0,52,97,97,33,8,31,291,4,NULL,'Group Assessment','LECTURER:8','FINAL','2026-08-18 01:28:03'),(2,0,_binary '\0',0,52,97,97,34,8,26,290,NULL,NULL,'Individual Assessment','LECTURER:8','FINAL','2026-08-18 01:28:03'),(5,0,_binary '\0',0,52,97,97,35,8,38,292,5,NULL,'Individual Assessment','LECTURER:8','FINAL','2026-08-18 01:28:03'),(5,0,_binary '\0',0,52,97,97,36,8,36,292,NULL,NULL,'Individual Assessment','LECTURER:8','FINAL','2026-08-18 01:28:03'),(3,2,_binary '\0',2,55,95,95,37,NULL,56,296,NULL,NULL,'Self Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,2,_binary '\0',2,55,97,95,39,NULL,56,296,NULL,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,2,_binary '\0',2,55,98,95,40,NULL,56,296,NULL,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,2,_binary '\0',2,55,96,95,41,NULL,56,296,NULL,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,3,_binary '\0',3,55,96,96,42,NULL,57,296,NULL,NULL,'Self Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,2,_binary '\0',2,55,95,96,43,NULL,56,296,NULL,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,4,_binary '\0',4,55,97,96,44,NULL,58,296,NULL,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,3,_binary '\0',3,55,98,96,45,NULL,57,296,NULL,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,1,_binary '\0',1,55,97,97,46,NULL,55,296,NULL,NULL,'Self Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,3,_binary '\0',3,55,95,97,47,NULL,57,296,NULL,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,3,_binary '\0',3,55,96,97,48,NULL,57,296,NULL,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,3,_binary '\0',3,55,98,97,49,NULL,57,296,NULL,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,4,_binary '\0',4,55,98,98,50,NULL,58,296,NULL,NULL,'Self Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,4,_binary '\0',4,55,95,98,51,NULL,58,296,NULL,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,4,_binary '\0',4,55,96,98,52,NULL,58,296,NULL,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,2,_binary '\0',2,55,97,98,53,NULL,56,296,NULL,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,3,_binary '\0',3,53,97,95,54,NULL,42,293,6,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,3,_binary '\0',3,53,97,95,55,NULL,47,294,NULL,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,4,_binary '\0',4,53,98,95,56,NULL,43,293,6,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,4,_binary '\0',4,53,98,95,57,NULL,48,294,NULL,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,4,_binary '\0',4,53,96,96,58,NULL,43,293,6,NULL,'Self Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,4,_binary '\0',4,53,96,96,59,NULL,48,294,NULL,NULL,'Self Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,3,_binary '\0',3,53,95,96,60,NULL,42,293,6,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,3,_binary '\0',3,53,95,96,61,NULL,47,294,NULL,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,2,_binary '\0',2,53,97,96,62,NULL,41,293,6,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,3,_binary '\0',3,53,97,96,63,NULL,47,294,NULL,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,3,_binary '\0',3,53,98,96,64,NULL,42,293,6,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,3,_binary '\0',3,53,98,96,65,NULL,47,294,NULL,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,4,_binary '\0',4,53,98,98,66,NULL,43,293,6,NULL,'Self Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,4,_binary '\0',4,53,98,98,67,NULL,48,294,NULL,NULL,'Self Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,3,_binary '\0',3,53,95,98,68,NULL,42,293,6,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,2,_binary '\0',2,53,95,98,69,NULL,46,294,NULL,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,2,_binary '\0',2,53,96,98,70,NULL,41,293,6,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,3,_binary '\0',3,53,96,98,71,NULL,47,294,NULL,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,3,_binary '\0',3,53,97,98,72,NULL,42,293,6,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,3,_binary '\0',3,53,97,98,73,NULL,47,294,NULL,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,3,_binary '\0',3,53,97,97,74,NULL,42,293,6,NULL,'Self Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,3,_binary '\0',3,53,97,97,75,NULL,47,294,NULL,NULL,'Self Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,3,_binary '\0',3,53,95,97,76,NULL,42,293,6,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,3,_binary '\0',3,53,95,97,77,NULL,47,294,NULL,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,4,_binary '\0',4,53,96,97,78,NULL,43,293,6,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,2,_binary '\0',2,53,96,97,79,NULL,46,294,NULL,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,0,_binary '\0',0,53,98,97,80,NULL,39,293,6,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,1,_binary '\0',1,53,98,97,81,NULL,44,294,NULL,NULL,'Peer Assessment',NULL,'FINAL','2026-08-18 01:28:03'),(3,2,_binary '\0',2,55,96,96,82,8,56,296,NULL,NULL,'Individual Assessment','LECTURER:8','FINAL','2026-08-18 01:28:03'),(3,3,_binary '\0',3,55,97,97,83,8,57,296,NULL,NULL,'Individual Assessment','LECTURER:8','FINAL','2026-08-18 01:28:03'),(3,4,_binary '\0',4,55,98,98,84,8,58,296,NULL,NULL,'Individual Assessment','LECTURER:8','FINAL','2026-08-18 01:28:03'),(3,3,_binary '\0',3,55,95,95,85,8,57,296,NULL,NULL,'Individual Assessment','LECTURER:8','FINAL','2026-08-18 01:28:03'),(3,3,_binary '\0',3,56,96,96,86,8,62,297,NULL,NULL,'Individual Assessment','LECTURER:8','FINAL','2026-08-18 01:28:03'),(2,3,_binary '\0',3,52,95,95,87,8,34,291,4,NULL,'Group Assessment','LECTURER:8','FINAL','2026-10-27 17:13:00'),(2,4,_binary '\0',4,52,95,95,88,8,30,290,NULL,NULL,'Individual Assessment','LECTURER:8','FINAL','2026-10-27 17:13:00'),(5,0,_binary '\0',0,52,95,95,89,8,38,292,5,NULL,'Individual Assessment','LECTURER:8','FINAL','2026-10-27 17:13:00'),(5,1,_binary '\0',1,52,95,95,90,8,37,292,NULL,NULL,'Individual Assessment','LECTURER:8','FINAL','2026-10-27 17:13:00');
/*!40000 ALTER TABLE `marks` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `project_group`
--

DROP TABLE IF EXISTS `project_group`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_group` (
  `groupSize` int NOT NULL,
  `academic_supervisor_id` bigint DEFAULT NULL,
  `course_id` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `industrial_supervisor_id` bigint DEFAULT NULL,
  `groupName` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_group_course` (`course_id`),
  KEY `idx_group_academic_sup` (`academic_supervisor_id`),
  KEY `idx_group_industrial_sup` (`industrial_supervisor_id`),
  CONSTRAINT `FK3w9soxexwe232nd105g18p1i0` FOREIGN KEY (`academic_supervisor_id`) REFERENCES `lecturer` (`id`),
  CONSTRAINT `FKs77rd8uch85qn6fwp580o315a` FOREIGN KEY (`industrial_supervisor_id`) REFERENCES `lecturer` (`id`),
  CONSTRAINT `FKt8v5ohgxinvv25b763i71p0cv` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `project_group`
--

LOCK TABLES `project_group` WRITE;
/*!40000 ALTER TABLE `project_group` DISABLE KEYS */;
INSERT INTO `project_group` VALUES (4,8,16,11,8,'Group 1'),(4,8,16,13,9,'Group 2'),(3,13,18,14,13,'Group 1');
/*!40000 ALTER TABLE `project_group` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rating`
--

DROP TABLE IF EXISTS `rating`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rating` (
  `marks` decimal(38,2) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rubric_id` bigint DEFAULT NULL,
  `sub_rubric_id` bigint DEFAULT NULL,
  `description` text,
  `name` varchar(10000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKm6862c2d6egk4wmcy66odak0e` (`rubric_id`),
  KEY `FK11ex9bslyaw233sq94h1bq8fa` (`sub_rubric_id`),
  CONSTRAINT `FK11ex9bslyaw233sq94h1bq8fa` FOREIGN KEY (`sub_rubric_id`) REFERENCES `sub_rubric` (`id`),
  CONSTRAINT `FKm6862c2d6egk4wmcy66odak0e` FOREIGN KEY (`rubric_id`) REFERENCES `rubric` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=69 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rating`
--

LOCK TABLES `rating` WRITE;
/*!40000 ALTER TABLE `rating` DISABLE KEYS */;
INSERT INTO `rating` VALUES (0.00,26,290,NULL,'etc','0 : Never (0% of the time)'),(1.00,27,290,NULL,'etc','1 : Rarely (25% of the time)'),(2.00,28,290,NULL,'etc','2 : Sometimes (50% of the time)'),(3.00,29,290,NULL,'etc','3 : Frequently (75% of the time)'),(4.00,30,290,NULL,'etc','4 : Everytime (100% of the time)'),(0.00,31,NULL,4,'etc','0'),(1.00,32,NULL,4,'etc','1'),(2.00,33,NULL,4,'etc','2'),(3.00,34,NULL,4,'etc','3'),(4.00,35,NULL,4,'etc','4'),(0.00,36,292,NULL,'etc','0 : Never (0% of the time)'),(1.00,37,292,NULL,'etc','1 : Rarely (25% of the time)'),(0.00,38,NULL,5,'etc','0'),(0.00,39,NULL,6,'none','0'),(1.00,40,NULL,6,'none','1'),(2.00,41,NULL,6,'none','2'),(3.00,42,NULL,6,'none','3'),(4.00,43,NULL,6,'none','4'),(1.00,44,294,NULL,'none','0 : Never (0% of the time)'),(1.00,45,294,NULL,'none','1 : Rarely (25% of the time)'),(2.00,46,294,NULL,'none','2'),(3.00,47,294,NULL,'none','3'),(4.00,48,294,NULL,'none','4 : Everytime (100% of the time)'),(0.00,54,296,NULL,'etc','0 : Never (0% of the time)'),(1.00,55,296,NULL,'etc','1 : Rarely (25% of the time)'),(2.00,56,296,NULL,'etc','2 : Sometimes (50% of the time)'),(3.00,57,296,NULL,'etc','3 : Frequently (75% of the time)'),(4.00,58,296,NULL,'etc','4 : Everytime (100% of the time)'),(0.00,59,297,NULL,'','0 : Never (0% of the time)'),(1.00,60,297,NULL,'','1 : Rarely (25% of the time)'),(2.00,61,297,NULL,'','2 : Sometimes (50% of the time)'),(3.00,62,297,NULL,'','3 : Frequently (75% of the time)'),(4.00,63,297,NULL,'','4 : Everytime (100% of the time)'),(5.00,64,NULL,7,'etc','Rating 1'),(4.00,65,NULL,7,'etc','Rating 2'),(3.00,66,NULL,7,'etc','Rating 3'),(2.00,67,NULL,7,'etc','Rating 4'),(1.00,68,NULL,7,'etc','Rating 5');
/*!40000 ALTER TABLE `rating` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rubric`
--

DROP TABLE IF EXISTS `rubric`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rubric` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `description` varchar(255) DEFAULT NULL,
  `marks` decimal(38,2) DEFAULT NULL,
  `name` varchar(10000) DEFAULT NULL,
  `assessment_id` bigint DEFAULT NULL,
  `clo` int DEFAULT NULL,
  `clo_marks` double DEFAULT NULL,
  `cloMarks` double DEFAULT NULL,
  `assessmentTypes` varchar(255) DEFAULT NULL,
  `rubric_comment_count` int DEFAULT NULL,
  `rubric_comment_min_length` int DEFAULT NULL,
  `comments_anonymous` bit(1) DEFAULT NULL,
  `display_order` int DEFAULT NULL,
  `assessment_type` varchar(255) DEFAULT NULL,
  `is_template` bit(1) DEFAULT NULL,
  `is_template_container` bit(1) DEFAULT NULL,
  `template_id` bigint DEFAULT NULL,
  `template_container_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK9kmhd8oheh26affp5tjtl3xx7` (`assessment_id`),
  KEY `FKjhv1wlpk3lrir4a3g42p15qvg` (`template_id`),
  KEY `FKrv10ovcgkct0mqreerxdiqw0a` (`template_container_id`),
  CONSTRAINT `FK9kmhd8oheh26affp5tjtl3xx7` FOREIGN KEY (`assessment_id`) REFERENCES `assessment` (`id`),
  CONSTRAINT `FKd1gshk694c4ge671s7nh4lh91` FOREIGN KEY (`assessment_id`) REFERENCES `assessment` (`id`),
  CONSTRAINT `FKjhv1wlpk3lrir4a3g42p15qvg` FOREIGN KEY (`template_id`) REFERENCES `rubric` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=299 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rubric`
--

LOCK TABLES `rubric` WRITE;
/*!40000 ALTER TABLE `rubric` DISABLE KEYS */;
INSERT INTO `rubric` VALUES (290,'etcf',1.00,'Market Survey',52,2,NULL,1,'Individual Assessment',NULL,NULL,NULL,0,NULL,NULL,NULL,NULL,NULL),(291,'etc',1.00,'Site Selection',52,2,NULL,1,'Group Assessment',NULL,NULL,NULL,0,NULL,NULL,NULL,NULL,NULL),(292,'etc',1.00,'test sub rubric and direct rating together',52,5,NULL,1,'Individual Assessment',NULL,NULL,NULL,1,NULL,NULL,NULL,NULL,NULL),(293,'combine confident body language, clear message structuring, and engaging delivery to inform and persuade your audience',1.30,'Presentation Skills',53,3,NULL,1.3,'Individual Assessment',NULL,NULL,NULL,0,NULL,NULL,NULL,NULL,NULL),(294,'serves specific industrial, commercial, or consumer purposes. These span raw elements, intermediate manufacturing materials, and finished consumer',1.20,'Chemical Product Background',53,3,NULL,1.2,'Individual Assessment',NULL,NULL,NULL,1,NULL,NULL,NULL,NULL,NULL),(296,'etc',1.00,'Market Surveye',55,3,NULL,1,'Individual Assessment',NULL,NULL,NULL,0,NULL,NULL,NULL,NULL,NULL),(297,'etc',1.00,'Site Selection',56,3,NULL,1,'Individual Assessment',NULL,NULL,NULL,0,NULL,NULL,NULL,NULL,NULL),(298,'etc',1.00,'Market Survey',56,3,NULL,1,'Individual Assessment',NULL,NULL,NULL,1,NULL,NULL,NULL,NULL,NULL);
/*!40000 ALTER TABLE `rubric` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rubric_comment_anonymous_flags`
--

DROP TABLE IF EXISTS `rubric_comment_anonymous_flags`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rubric_comment_anonymous_flags` (
  `comment_anonymous` bit(1) DEFAULT NULL,
  `rubric_id` bigint NOT NULL,
  KEY `FKm4vjld8nqt49pw40ltcdyg4d0` (`rubric_id`),
  CONSTRAINT `FKm4vjld8nqt49pw40ltcdyg4d0` FOREIGN KEY (`rubric_id`) REFERENCES `rubric` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rubric_comment_anonymous_flags`
--

LOCK TABLES `rubric_comment_anonymous_flags` WRITE;
/*!40000 ALTER TABLE `rubric_comment_anonymous_flags` DISABLE KEYS */;
INSERT INTO `rubric_comment_anonymous_flags` VALUES (_binary '',283),(_binary '\0',289);
/*!40000 ALTER TABLE `rubric_comment_anonymous_flags` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rubric_comment_labels`
--

DROP TABLE IF EXISTS `rubric_comment_labels`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rubric_comment_labels` (
  `rubric_id` bigint NOT NULL,
  `comment_label` varchar(500) DEFAULT NULL,
  KEY `FK1b2u1s6w44lm7aut0jnu8u27h` (`rubric_id`),
  CONSTRAINT `FK1b2u1s6w44lm7aut0jnu8u27h` FOREIGN KEY (`rubric_id`) REFERENCES `rubric` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rubric_comment_labels`
--

LOCK TABLES `rubric_comment_labels` WRITE;
/*!40000 ALTER TABLE `rubric_comment_labels` DISABLE KEYS */;
INSERT INTO `rubric_comment_labels` VALUES (283,'edtsdfdsfdsf'),(289,'adasaadassxsaasdasda');
/*!40000 ALTER TABLE `rubric_comment_labels` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rubric_comment_min_lengths`
--

DROP TABLE IF EXISTS `rubric_comment_min_lengths`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rubric_comment_min_lengths` (
  `comment_min_length` int DEFAULT NULL,
  `rubric_id` bigint NOT NULL,
  KEY `FKd9sy6ixarpvhw3we01oxhrj7p` (`rubric_id`),
  CONSTRAINT `FKd9sy6ixarpvhw3we01oxhrj7p` FOREIGN KEY (`rubric_id`) REFERENCES `rubric` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rubric_comment_min_lengths`
--

LOCK TABLES `rubric_comment_min_lengths` WRITE;
/*!40000 ALTER TABLE `rubric_comment_min_lengths` DISABLE KEYS */;
INSERT INTO `rubric_comment_min_lengths` VALUES (20,283),(20,289);
/*!40000 ALTER TABLE `rubric_comment_min_lengths` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rubric_rubriccommentanonymousflags`
--

DROP TABLE IF EXISTS `rubric_rubriccommentanonymousflags`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rubric_rubriccommentanonymousflags` (
  `Rubric_id` bigint NOT NULL,
  `comment_anonymous` bit(1) DEFAULT NULL,
  KEY `FKsxse3snjkyoskj3rde10c4yrl` (`Rubric_id`),
  CONSTRAINT `FKsxse3snjkyoskj3rde10c4yrl` FOREIGN KEY (`Rubric_id`) REFERENCES `rubric` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rubric_rubriccommentanonymousflags`
--

LOCK TABLES `rubric_rubriccommentanonymousflags` WRITE;
/*!40000 ALTER TABLE `rubric_rubriccommentanonymousflags` DISABLE KEYS */;
/*!40000 ALTER TABLE `rubric_rubriccommentanonymousflags` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rubric_rubriccommentlabels`
--

DROP TABLE IF EXISTS `rubric_rubriccommentlabels`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rubric_rubriccommentlabels` (
  `Rubric_id` bigint NOT NULL,
  `comment_label` varchar(500) DEFAULT NULL,
  KEY `FKmwj4fm5jgvdx20wofr3arckfi` (`Rubric_id`),
  CONSTRAINT `FKmwj4fm5jgvdx20wofr3arckfi` FOREIGN KEY (`Rubric_id`) REFERENCES `rubric` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rubric_rubriccommentlabels`
--

LOCK TABLES `rubric_rubriccommentlabels` WRITE;
/*!40000 ALTER TABLE `rubric_rubriccommentlabels` DISABLE KEYS */;
/*!40000 ALTER TABLE `rubric_rubriccommentlabels` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rubric_rubriccommentminlengths`
--

DROP TABLE IF EXISTS `rubric_rubriccommentminlengths`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rubric_rubriccommentminlengths` (
  `Rubric_id` bigint NOT NULL,
  `comment_min_length` int DEFAULT NULL,
  KEY `FKqmjlw6vjcdmp31ekcn1di2f3n` (`Rubric_id`),
  CONSTRAINT `FKqmjlw6vjcdmp31ekcn1di2f3n` FOREIGN KEY (`Rubric_id`) REFERENCES `rubric` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rubric_rubriccommentminlengths`
--

LOCK TABLES `rubric_rubriccommentminlengths` WRITE;
/*!40000 ALTER TABLE `rubric_rubriccommentminlengths` DISABLE KEYS */;
/*!40000 ALTER TABLE `rubric_rubriccommentminlengths` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `rubric_templates`
--

DROP TABLE IF EXISTS `rubric_templates`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `rubric_templates` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `description` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `structureData` json DEFAULT NULL,
  `course_id` bigint DEFAULT NULL,
  `template_type` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_rubric_template_course` (`course_id`),
  CONSTRAINT `FKeikdpxre2aj6b0ytf0ne3vg04` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `rubric_templates`
--

LOCK TABLES `rubric_templates` WRITE;
/*!40000 ALTER TABLE `rubric_templates` DISABLE KEYS */;
INSERT INTO `rubric_templates` VALUES (3,'etc','Market Survey','{\"clo\": \"3\", \"marks\": \"1\", \"comments\": [], \"subRubrics\": [{\"name\": \"Sub Rubric 1\", \"ratings\": [{\"name\": \"Rating 1\", \"marks\": \"5\", \"description\": \"etc\"}, {\"name\": \"Rating 2\", \"marks\": \"4\", \"description\": \"etc\"}, {\"name\": \"Rating 3\", \"marks\": \"3\", \"description\": \"etc\"}, {\"name\": \"Rating 4\", \"marks\": \"2\", \"description\": \"etc\"}, {\"name\": \"Rating 5\", \"marks\": \"1\", \"description\": \"etc\"}], \"description\": \"etc\"}], \"templateName\": \"Market Survey\", \"directRatings\": [], \"assessmentType\": \"Individual Assessment\", \"templateDescription\": \"etc\"}',16,'FULL');
/*!40000 ALTER TABLE `rubric_templates` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `student`
--

DROP TABLE IF EXISTS `student`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `password` varchar(255) NOT NULL,
  `username` varchar(255) DEFAULT NULL,
  `group_id` bigint DEFAULT NULL,
  `email` varchar(255) NOT NULL,
  `resetPasswordToken` varchar(255) DEFAULT NULL,
  `is_temp_password` tinyint(1) DEFAULT '0',
  `isTempPassword` tinyint(1) NOT NULL DEFAULT '0',
  `course_id` bigint DEFAULT NULL,
  `resetpasswordtokenexpiry` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKjyet50p17q01ks2bv4sn8i5r7` (`username`),
  UNIQUE KEY `idx_student_username` (`username`),
  UNIQUE KEY `uk_student_email_course` (`email`,`course_id`),
  UNIQUE KEY `idx_student_email_course` (`email`,`course_id`),
  KEY `FKdl1txk23pxukm5i26tbgemt70` (`group_id`),
  KEY `idx_student_course` (`course_id`),
  KEY `idx_student_group` (`group_id`),
  CONSTRAINT `fk_student_course` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`),
  CONSTRAINT `FKdl1txk23pxukm5i26tbgemt70` FOREIGN KEY (`group_id`) REFERENCES `project_group` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=110 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `student`
--

LOCK TABLES `student` WRITE;
/*!40000 ALTER TABLE `student` DISABLE KEYS */;
INSERT INTO `student` VALUES (95,'$2a$12$mWfzf0AA59pJtZyZBGP3GeU8ZmYmKLakgN0KeY/rcXuajlNmLWKI6','zizan',11,'zizan@gmail.com','4f7ab3b4-30ac-415d-86c0-475ba7584a29',0,1,16,NULL),(96,'$2a$12$IFVREdF.F0huwcnlHqELtOviGy0FNFYfcZdPhU07y7ul2nFJnooJK','adam',11,'adam@gmail.com','84ea4965-6e4f-4f18-83e6-4d850e7a8088',0,1,16,NULL),(97,'$2a$12$IFVREdF.F0huwcnlHqELtOviGy0FNFYfcZdPhU07y7ul2nFJnooJK','diyana',11,'diyana@gmail.com','e92f0ee9-55ac-4ea0-842c-da47ed298fc9',0,1,16,NULL),(98,'$2a$12$IFVREdF.F0huwcnlHqELtOviGy0FNFYfcZdPhU07y7ul2nFJnooJK','hakim',11,'hakim@gmail.com','d0c4e552-0fa0-4346-93b0-d09f9f3a48e7',0,1,16,NULL),(103,'$2a$10$.4L2rIXTRzXy6Jzq6S8nkuo3PXD2/CDPZntco2BHjmAnDlnkzr2r.','Anas',13,'anas@gmail.com','770f11b5-4e70-406e-b17b-1b685d2ecf4b',0,1,16,NULL),(104,'$2a$10$4927ocMJsdHC6vBuvGxMuug9EjmfehRqUIGhVTPGzoOzYmA0/9HXq','Aina',13,'aina@gmail.com','9349157d-7a25-4fa5-804f-b1c2b95e7bb0',0,1,16,NULL),(105,'$2a$10$zboLLWCujFybrhfe6jx.Q..3MX.Yfq2wLK8PiTxAaWj0PCFBMIgRG','Hajar',13,'hajar@gmail.com','922b3f97-827d-4ab6-aedb-db6caeeffe81',0,1,16,NULL),(106,'$2a$10$xoQGMYjvGH/IFp63SV2TdepGeXP4L5vPBD0nvuwRmKobJUuO2rXPq','Alif',13,'alif@gmail.com','da59bd20-5efa-48d4-9ad5-05ee44088024',0,1,16,NULL),(107,'$2a$10$JE0VSO39xOSS.Fh9kXrIR.N2TCpgiZcYj2deng09j1xvt6Bm8kVO.','Khai',14,'khai@gmail.com','8b03ccfa-82be-4c11-be3d-34b94851a25e',0,1,18,NULL),(108,'$2a$10$fY0yvDXcpiZXh2gEZAF6VeABywMJi75fj./SuhkR7c1WrTOj25MLq','Liyana',14,'liyana@gmail.com','3aad0ac0-0b43-42d9-b7fb-19f22e20d2c2',0,1,18,NULL),(109,'$2a$10$RjcMQfBTUnn.4DvrAZAGjuzY4gqD0tbW3bQefR3g5MFHRQG3oL5rm','Lydia',14,'lydia@gmail.com','81c79d9b-db83-40db-8964-5845463904c4',0,1,18,NULL);
/*!40000 ALTER TABLE `student` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `student_assessment_assignment`
--

DROP TABLE IF EXISTS `student_assessment_assignment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_assessment_assignment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `assessment_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `peer_assessment` bit(1) DEFAULT NULL,
  `self_assessment` bit(1) DEFAULT NULL,
  `group_assessment` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_saa_assessment_student` (`assessment_id`,`student_id`),
  KEY `idx_saa_assessment_student` (`assessment_id`,`student_id`),
  KEY `idx_saa_student` (`student_id`),
  CONSTRAINT `FKll2u54pri2pr990edg5u80b9p` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`) ON DELETE CASCADE,
  CONSTRAINT `FKpon5pejqbl9p0gal856tkvysg` FOREIGN KEY (`assessment_id`) REFERENCES `assessment` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `student_assessment_assignment`
--

LOCK TABLES `student_assessment_assignment` WRITE;
/*!40000 ALTER TABLE `student_assessment_assignment` DISABLE KEYS */;
INSERT INTO `student_assessment_assignment` VALUES (13,53,96,_binary '',_binary '',_binary ''),(14,53,97,_binary '',_binary '',_binary ''),(15,53,98,_binary '',_binary '',_binary ''),(16,53,95,_binary '',_binary '',_binary ''),(17,52,96,_binary '',_binary '',_binary ''),(18,52,95,_binary '',_binary '',_binary '');
/*!40000 ALTER TABLE `student_assessment_assignment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `student_course_assignment`
--

DROP TABLE IF EXISTS `student_course_assignment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_course_assignment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sca_student_course` (`student_id`,`course_id`),
  KEY `idx_sca_course` (`course_id`),
  KEY `idx_sca_student` (`student_id`),
  CONSTRAINT `FKd1s37ixx2rn3rpht56yvxrywi` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`),
  CONSTRAINT `FKojm8rp11v43tbs7l6nvyc5n9x` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `student_course_assignment`
--

LOCK TABLES `student_course_assignment` WRITE;
/*!40000 ALTER TABLE `student_course_assignment` DISABLE KEYS */;
INSERT INTO `student_course_assignment` VALUES (4,16,95),(5,16,96),(6,16,97),(7,16,98),(12,16,103),(13,16,104),(14,16,105),(15,16,106),(16,18,107),(17,18,108),(18,18,109);
/*!40000 ALTER TABLE `student_course_assignment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `student_result_overrides`
--

DROP TABLE IF EXISTS `student_result_overrides`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_result_overrides` (
  `overriddenFactor` double DEFAULT NULL,
  `overriddenGrandTotal` double DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK723gnu6ytpe1lliq4hvf7msxf` (`student_id`),
  CONSTRAINT `FKmoav9p5qqw4i8g04n4r4ot8nw` FOREIGN KEY (`student_id`) REFERENCES `student` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `student_result_overrides`
--

LOCK TABLES `student_result_overrides` WRITE;
/*!40000 ALTER TABLE `student_result_overrides` DISABLE KEYS */;
INSERT INTO `student_result_overrides` VALUES (0.7,NULL,1,96);
/*!40000 ALTER TABLE `student_result_overrides` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sub_rubric`
--

DROP TABLE IF EXISTS `sub_rubric`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sub_rubric` (
  `marks` decimal(38,2) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rubric_id` bigint DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `name` varchar(10000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK893xntvwjgqypmlcxt35n4ldl` (`rubric_id`),
  CONSTRAINT `FK893xntvwjgqypmlcxt35n4ldl` FOREIGN KEY (`rubric_id`) REFERENCES `rubric` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sub_rubric`
--

LOCK TABLES `sub_rubric` WRITE;
/*!40000 ALTER TABLE `sub_rubric` DISABLE KEYS */;
INSERT INTO `sub_rubric` VALUES (4.00,4,291,'etc','Sub Rubric 1'),(0.00,5,292,'etc','Sub Rubric 1'),(4.00,6,293,'none','Confidence, eye contact, language proficiency, personal appearance and presentation skills'),(5.00,7,298,'etc','Sub Rubric 1');
/*!40000 ALTER TABLE `sub_rubric` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `submission_annotations`
--

DROP TABLE IF EXISTS `submission_annotations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `submission_annotations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `page_number` int NOT NULL,
  `strokes_json` longtext,
  `updated_at` datetime(6) NOT NULL,
  `lecturer_id` bigint NOT NULL,
  `submission_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_annotation_submission` (`submission_id`),
  KEY `idx_annotation_lecturer` (`lecturer_id`),
  KEY `idx_annotation_page` (`submission_id`,`page_number`),
  CONSTRAINT `FKg65l6cjnb70y7wgg9p9galcs8` FOREIGN KEY (`submission_id`) REFERENCES `assessment_submissions` (`id`),
  CONSTRAINT `FKkyxrxw0otbs5ilwhlhtbpdhi5` FOREIGN KEY (`lecturer_id`) REFERENCES `lecturer` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `submission_annotations`
--

LOCK TABLES `submission_annotations` WRITE;
/*!40000 ALTER TABLE `submission_annotations` DISABLE KEYS */;
INSERT INTO `submission_annotations` VALUES (1,1,'[{\"tool\":\"text\",\"text\":\"very good 100%\",\"color\":\"#4A0018\",\"size\":13,\"x\":0.807439072327044,\"y\":0.07022848330827655,\"cachedBox\":{\"x\":513.53125,\"y\":63.13540649414062,\"width\":133.959810886335,\"height\":35.98816120906801}}]','2026-10-27 17:12:42.315104',8,1);
/*!40000 ALTER TABLE `submission_annotations` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `super_admin`
--

DROP TABLE IF EXISTS `super_admin`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `super_admin` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `email` varchar(255) NOT NULL,
  `password` varchar(255) NOT NULL,
  `resetPasswordToken` varchar(255) DEFAULT NULL,
  `roles` varchar(255) NOT NULL,
  `username` varchar(255) NOT NULL,
  `resetpasswordtokenexpiry` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKn117088hklqvqcyo4mkbwya7l` (`email`),
  UNIQUE KEY `UKgplfhh96i6l9o5f2n9m966utq` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `super_admin`
--

LOCK TABLES `super_admin` WRITE;
/*!40000 ALTER TABLE `super_admin` DISABLE KEYS */;
INSERT INTO `super_admin` VALUES (1,'ihsan.rubix@gmail.com','$2a$12$ssFqUcCwwdaQSCaDc.21mOJJTXnTd2kro18e8/AjMCaiZyjrCn./e',NULL,'ROLE_SUPER_ADMIN,ROLE_ADMIN,ROLE_LECTURER','superadmin',NULL);
/*!40000 ALTER TABLE `super_admin` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-28  5:18:54
