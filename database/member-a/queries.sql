-- Q-A1 单表条件查询：种子数据预期1001、1002、1007、1010。
SELECT id, student_no, name, major FROM student
WHERE major = '计算机科学与技术' ORDER BY student_no;

-- Q-A2 INNER JOIN：种子数据学生1001两条，包含未出成绩记录。
SELECT s.student_no, s.name, tc.class_code, c.name AS course_name,
       e.status, e.score, e.selected_at
FROM student s INNER JOIN enrollment e ON e.student_id = s.id
INNER JOIN teaching_class tc ON tc.id = e.teaching_class_id
INNER JOIN course c ON c.id = tc.course_id
WHERE s.id = 1001 AND e.status = 'ENROLLED'
ORDER BY tc.class_code;

-- Q-A3 嵌套查询：没有有效选课的学生，包括只有退课记录者。预期1008、1010。
SELECT s.id, s.student_no, s.name FROM student s
WHERE NOT EXISTS (
    SELECT 1 FROM enrollment e WHERE e.student_id = s.id AND e.status = 'ENROLLED'
) ORDER BY s.student_no;

-- Q-A4 JDBC的第二条多表查询：已出成绩的个人成绩单。
-- 种子学生1001仅1条90.50，未出成绩不混入统计。
SELECT s.student_no, s.name, tc.class_code, c.name AS course_name,
       c.credits, e.score
FROM student s INNER JOIN enrollment e ON e.student_id = s.id
INNER JOIN teaching_class tc ON tc.id = e.teaching_class_id
INNER JOIN course c ON c.id = tc.course_id
WHERE s.id = 1001 AND e.status = 'ENROLLED' AND e.score IS NOT NULL
ORDER BY tc.class_code;
