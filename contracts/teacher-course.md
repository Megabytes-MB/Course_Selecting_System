# B 模块教师课程接口契约

成员 B 按集成基线 v1.0 实现教师、课程及公共 Java 工程。2026-11-25 已完成 B 实施和独立验证；本文不代替 A、C 的审核，也不更改共享基线的确认栏。

## 数据与职责

`teacher(id BIGINT, teacher_no VARCHAR(20), name VARCHAR(50))`，`course(id BIGINT, course_code VARCHAR(20), name VARCHAR(100), credits DECIMAL(4,1))`。主键自增、有符号，其他字段非空；教师编号、课程代码分别唯一。文本禁止纯空白；学分大于零。Java 主键对应 `long`，学分对应 `BigDecimal`。

编号固定：教师 1101—1110，课程 1201—1210。模块 SQL 不创建、切换或删除数据库；初始化只运行一次，不覆盖既有记录。主线脚本顺序仍为 01/02/03/04，随后 11/12/13/14。

教师和课程通过 C 的 `teaching_class.teacher_id/course_id` 关联，一门课程可以有多个班、不同班可以由不同教师授课。B 不在课程表添加教师外键。C 的外键必须使用 `RESTRICT`，禁止删除已引用的教师和课程。DAO 捕获前不以“先查是否引用”替代数据库外键，避免并发检查失效。

## Java 入口

`edu.project.catalog.CatalogMenu` 提供无参构造器与 `public void run(Scanner scanner)`。菜单不关闭调用方的 Scanner。`TeacherCourseService` 提供教师和课程完整 CRUD、按最低学分查课、教师教学班和按学期查课。

`TeacherDAO`、`CourseDAO` 的数据库方法均接收 `Connection`，不创建或关闭该连接，不提交或回滚。查询返回 `Optional` 或列表；新增返回生成 ID；更新对存在但值未变化的行返回 true，不存在返回 false；删除不存在记录返回 false。写入检查影响行数；更新在影响行数为零时核对记录是否存在，兼容驱动 `useAffectedRows=true`。动态输入全部使用 `PreparedStatement`。

学分写入要求数值在 0.1—999.9 且最多一位有效小数；3.50 可无损表示为 3.5，3.55 拒绝。`DECIMAL` 可能在 CHECK 前舍入，故 Java 使用 `RoundingMode.UNNECESSARY` 检查。SQL 直接写入的精度行为不能描述为全部由 CHECK 拒绝。

## 跨模块只读查询

1. `credits >= ? ORDER BY credits DESC, course_code`，初始化中阈值 3.0 返回六门课程。
2. 教师 LEFT JOIN 教学班、课程、学期，保留未开课教师；查询粒度为教师与教学班的组合，未开课教师对应一行空班级。
3. `course.id IN (SELECT course_id FROM teaching_class WHERE semester_id=?)`，同课程多班只返回一门课程。开课指存在教学班，不要求已有学生选课。

B 不写入教学班或选课表，不另设选课/容量事务。全组复杂事务继续由 A、C 负责。B 的回滚测试仅验证 DAO 不擅自提交调用方事务。

## 当前对接状态

A 的真实学生模块已通过 `new StudentMenu(DBUtil::getConnection).run(scanner)` 接入。C 远端分支截至本次 fetch 为 `c29c123`，只有设计与契约，没有正式 Java/SQL；Main 按其约定加载无参 `TeachingMenu` 并调用 `run(Scanner)`，缺席时明确提示。B 测试目录的依赖夹具不属于 C 的正式实现。
