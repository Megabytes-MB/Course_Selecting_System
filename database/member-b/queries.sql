-- B-Q1: single-table condition and stable ordering; expected 6 rows in B seed data.
SELECT id, course_code, name, credits
FROM course
WHERE credits >= 3.0
ORDER BY credits DESC, course_code;

-- B-Q2: preserve teachers without classes; NULL class columns mean "not teaching".
-- Requires C's formal semester/teaching_class tables. Do not filter tc in WHERE.
SELECT t.id AS teacher_id, t.teacher_no, t.name AS teacher_name,
       tc.id AS teaching_class_id, tc.class_code,
       c.name AS course_name, s.name AS semester_name
FROM teacher t
LEFT JOIN teaching_class tc ON tc.teacher_id = t.id
LEFT JOIN course c ON c.id = tc.course_id
LEFT JOIN semester s ON s.id = tc.semester_id
ORDER BY t.teacher_no, tc.class_code;

-- B-Q3: courses actually offered in semester 1301, each course appears once.
-- "Offered" means that a teaching_class exists, regardless of enrollment count.
SELECT id, course_code, name, credits
FROM course
WHERE id IN (SELECT course_id FROM teaching_class WHERE semester_id = 1301)
ORDER BY course_code;
