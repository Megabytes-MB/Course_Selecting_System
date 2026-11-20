-- 成员A约束检查清单。请仅在独立测试库逐条执行，每条均应报错。
-- 不作为普通初始化脚本自动批量执行；自动化等价检查在ModuleChecks A11。
-- 以下依赖初始化记录3001、3009，失败后其值应保持不变。
UPDATE enrollment SET score = 101 WHERE id = 3001;
UPDATE enrollment SET status = 'BAD' WHERE id = 3001;
UPDATE enrollment SET status = 'enrolled' WHERE id = 3001;
UPDATE enrollment SET status = 'WITHDRAWN' WHERE id = 3001;
UPDATE enrollment SET score = 10 WHERE id = 3009;
UPDATE enrollment SET withdrawn_at = '2026-01-01 00:00:00' WHERE id = 3009;
INSERT INTO enrollment(student_id,teaching_class_id) VALUES(9999,2001);
INSERT INTO enrollment(student_id,teaching_class_id) VALUES(1001,9999);
INSERT INTO enrollment(student_id,teaching_class_id) VALUES(1001,2001);
INSERT INTO student(student_no,name,major) VALUES('BLANK','   ','专业');
