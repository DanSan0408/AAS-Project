CREATE TABLE admin (
id BIGINT IDENTITY PRIMARY KEY,
username VARCHAR(200) NOT NULL UNIQUE,
password VARCHAR(100) NOT NULL
);

CREATE TABLE students (
id BIGINT IDENTITY PRIMARY KEY,
username VARCHAR(200) NOT NULL UNIQUE,
password VARCHAR(100) NOT NULL
);

CREATE TABLE lecturers (
id BIGINT IDENTITY PRIMARY KEY,
username VARCHAR(200) NOT NULL UNIQUE,
password VARCHAR(100) NOT NULL
);

CREATE TABLE industrialsupervisor (
id BIGINT IDENTITY PRIMARY KEY,
username VARCHAR(200) NOT NULL UNIQUE,
password VARCHAR(100) NOT NULL
);

INSERT INTO admin (username, password) VALUES ('admin1', '123');
INSERT INTO students (username, password) VALUES ('student1', '123');
INSERT INTO lecturers (username, password) VALUES ('lecturer1', '123');
INSERT INTO industrialsupervisor (username, password) VALUES ('is1', '123');