# C 模块接口契约：学期与教学班

本文供 A 对接选课与成绩、B 对接教师课程和主菜单。C 维护 `semester`、`teaching_class` 及教学统计。共享字段、事务和脚本顺序以集成基线 v1.0 为准。本文记录拟交接的接口，A、B、C 的正式确认见文末。[三模块集成基线](integration-baseline.md) · [A 模块接口契约](student-enrollment.md)

## C 提供的表与外键

目标环境为 MySQL 8.4 LTS、InnoDB、`utf8mb4`。下表字段均为 `NOT NULL`，主键和外键使用有符号 `BIGINT`，对应 Java `long`。

| 表 | 字段 | SQL 类型 | 约束及用途 |
|---|---|---|---|
| `semester` | `id` | `BIGINT` | 自增主键，供 `teaching_class.semester_id` 引用 |
| `semester` | `name` | `VARCHAR(30)` | 学期名唯一，禁止空字符串和纯空白 |
| `semester` | `start_date` | `DATE` | 开始日期 |
| `semester` | `end_date` | `DATE` | 结束日期，须大于或等于 `start_date` |
| `teaching_class` | `id` | `BIGINT` | 自增主键，供 A 的 `enrollment.teaching_class_id` 引用 |
| `teaching_class` | `class_code` | `VARCHAR(30)` | 班号唯一，禁止空字符串和纯空白，供名单和成绩单展示 |
| `teaching_class` | `course_id` | `BIGINT` | 引用 B 的 `course.id`，供 A 关联课程名称与学分 |
| `teaching_class` | `teacher_id` | `BIGINT` | 引用 B 的 `teacher.id`，每班一名教师 |
| `teaching_class` | `semester_id` | `BIGINT` | 引用 C 的 `semester.id`，每班归属一个学期 |
| `teaching_class` | `capacity` | `INT` | 正整数，供 A 判断剩余名额，由 C 的服务调整 |

C 为 `teaching_class` 建立 `(semester_id, course_id)` 和 `(teacher_id)` 索引。外键使用 `RESTRICT` 保护被引用的数据。学期、课程或教师已有教学班时应拒绝删除。教学班已有选课记录时也应拒绝删除，包括仅有退课记录的情况。

## 三方建表与初始化顺序

| 批次 | 全组执行顺序 | C 的交付与依赖 |
|---|---|---|
| 建表 | A `01` 学生 → B `02` 教师、课程 → C `03` 学期、教学班 → A `04` 选课 | `database/member-c/03_create_semester_teaching.sql` 先建 `semester`，再建引用学期、课程和教师的 `teaching_class`。完成后，A 可建立选课到教学班的外键 |
| 初始化 | A `11` 学生 → B `12` 教师、课程 → C `13` 学期、教学班 → A `14` 选课 | `database/member-c/13_seed_semester_teaching.sql` 先插入学期 `1301–1310`，再插入教学班 `2001–2010`，引用 B 已插入的教师 `1101–1110`、课程 `1201–1210`。A 的选课 `3001–3010` 引用学生 `1001–1010` 和上述教学班 |

每张核心表至少提供 10 条有效记录。教学班 `2001–2010` 的**初始容量至少为 3**，以便三方使用同一批演示数据。初始化在新建的空测试库执行，模块脚本使用调用方选定的数据库。

## A 与 C 的容量事务和统计口径

A 的 `EnrollmentService`、`GradeService` 负责选课、退课和成绩写入。C 的名单、人数与成绩统计只读 `student`、`enrollment`；已建班的容量变更统一由 C 的 `TeachingClassService` 处理。选退课、录分和调容量均在 `READ COMMITTED` 下先锁同一条目标教学班记录，事务内各 DAO 复用同一个 JDBC `Connection`。

1. C 开启事务，用 `SELECT id FROM teaching_class WHERE id = ? FOR UPDATE` 锁住目标班。若 A 的操作还需锁定某条选课记录，须在取得教学班锁后再锁该记录。
2. C 在持锁连接上统计该班 `status='ENROLLED'` 的记录。新容量必须大于 0，且大于或等于当前有效人数；因此有 1 人的班允许缩容到 1。
3. 校验通过后更新容量，检查影响行数恰好为 1 后提交。找不到教学班、容量不合法或执行失败时回滚，并向调用方说明原因。

名单与人数只包含 `ENROLLED`；`WITHDRAWN` 记录保留在库中。分组统计按学期和课程汇总有效选课。已出成绩人数仅计 `score IS NOT NULL`，0 分计入，60 分及格。平均分和及格率均以已出成绩记录为基础，零分母时返回 `NULL`，菜单可显示“暂无成绩”。保留零选课班时，在 `LEFT JOIN` 的连接条件中过滤有效状态并计数 `enrollment.id`。

## B 与 C 的 Java 接入

B 维护 Maven 工程、`edu.project.Main` 和 `edu.project.common.DBUtil`。`DBUtil.getConnection()` 每次返回独立的、初始 `autoCommit=true` 的连接，由调用方关闭。C 的 DAO 接收连接并通过 `PreparedStatement` 绑定参数，事务的提交与回滚由服务层控制。

C 后续交付 `edu.project.teaching.TeachingMenu`，公开实例方法 `public void run(Scanner scanner)`。建议由 B 的 `Main` 调用 `new TeachingMenu().run(scanner)`，由 C 在模块内使用公共 `DBUtil.getConnection()`。无参构造方式供 B、C 审核。唯一的 `Scanner` 由 `Main` 创建和关闭，教学子菜单返回时将控制权交还主菜单。

## 交接确认

| 成员 | 待核对事项 | 状态 |
|---|---|---|
| A | 教学班外键和编号、有效选课定义、选退课及录分与调容量的锁顺序 | 待确认 |
| B | 教师与课程编号及外键类型、连接生命周期、主菜单调用方式 | 待确认 |
| C | 两张表的最小结构、种子与脚本顺序、容量事务和统计口径 | 待确认 |

正式确认由各成员在 PR 审核或共享基线中留痕。后续联调时，A 与 C 验证最后一个名额和并发缩容，B 与 C 验证 JDK 17 下从主菜单进入教学菜单并返回。[项目进度记录](../docs/项目进度记录.md)
