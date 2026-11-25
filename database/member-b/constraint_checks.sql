-- Manual negative cases, executed individually in an isolated test database.
-- Each statement below must fail. The automatic runner asserts error codes and unchanged rows.
-- Duplicate teacher number: 1062
INSERT INTO teacher(teacher_no,name) VALUES ('T001','重复教师');
-- Duplicate course code: 1062
INSERT INTO course(course_code,name,credits) VALUES ('CS001','重复课程',3.0);
-- Nonpositive credits: 3819
INSERT INTO course(course_code,name,credits) VALUES ('BAD_ZERO','非法课程',0);
-- Whitespace-only teacher name (tab): 3819
INSERT INTO teacher(teacher_no,name) VALUES ('BAD_NAME',CHAR(9));
-- Whitespace-only course code: 3819
INSERT INTO course(course_code,name,credits) VALUES ('   ','非法代码',1.0);
-- Required name: 1048
INSERT INTO course(course_code,name,credits) VALUES ('BAD_NULL',NULL,1.0);
-- RESTRICT checks require C's teaching_class to reference these specific records: 1451
DELETE FROM course WHERE id=1201;
DELETE FROM teacher WHERE id=1101;
-- DECIMAL may round extra fractional digits before CHECK evaluates.
-- Input precision is therefore also checked by CourseDAO using UNNECESSARY, not by CHECK alone.
