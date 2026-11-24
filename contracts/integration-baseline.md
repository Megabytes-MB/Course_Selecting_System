# 高校选课系统三模块集成基线

版本：1.0  
状态：团队开发基线，B、C开始实现前应完成确认  
适用分支：`member-a/student-enrollment`、`member-b/teacher-course`、`member-c/semester-teaching`及后续集成分支

## 1 文档目的与效力

本文档以A已完成的学生与选课成绩模块为基线，固定A、B、C之间共享的数据库结构、Java接入方式、事务锁协议、演示数据编号、文件所有权和联合验收方法。目标是让三人能够并行开发，并在合并时保持编译、建库和业务语义一致。

本文档只约束跨模块接口，不替代三人的模块设计文档。各成员可以在自己的表和包中增加不破坏兼容性的字段、索引、查询或辅助类，但不得自行修改本文档中的冻结项。

规则优先级如下：

1. 老师发布的课程作业要求。
2. 本集成基线中已经冻结的跨模块规则。
3. 各模块自己的契约、设计文档和实现细节。

若模块文档与本文档冲突，先停止涉及冲突部分的开发，由相关负责人共同修改本文档并记录迁移方法。未经确认的口头约定不作为集成依据。

## 2 确认与变更流程

本文档合入共同基线前，三名成员应确认下表。确认可以通过Pull Request审核记录完成，不要求在文件中填写真实姓名。

| 角色 | 必须确认的内容 | 确认结果 |
|---|---|---|
| A | `student`、`enrollment`字段，状态语义，选退课与成绩事务 | 待确认 |
| B | `teacher`、`course`最小字段，公共Java工程、`DBUtil`和`Main`接口 | 待确认 |
| C | `semester`、`teaching_class`最小字段，编号，容量锁协议和集成顺序 | 待确认 |

变更冻结项时必须执行以下流程：

1. 先修改本文档，说明变更原因、受影响模块和数据迁移或兼容方案。
2. 至少由受影响的表负责人和调用方共同确认。
3. 各负责人只修改自己拥有的源码和脚本。
4. 在同一个集成批次内完成数据库脚本、Java代码、测试和文档更新。
5. 在全新测试库重新执行完整建库、初始化和联合测试。

## 3 技术环境与通用命名

| 项目 | 冻结规则 |
|---|---|
| 数据库 | MySQL 8.4 LTS |
| 存储引擎 | InnoDB |
| 默认字符集 | `utf8mb4` |
| Java | JDK 17；允许使用`record`和Java 17 `switch`语法 |
| 数据访问 | 原生JDBC，不引入Spring等框架 |
| 主键和外键 | 有符号`BIGINT`，Java类型为`long` |
| 表名和列名 | 小写单数表名、`snake_case`列名 |
| 时间 | 数据库本地时间，时间列统一使用`DATETIME(6)`；三人使用同一数据库时区 |
| 金额和成绩等精确数值 | 使用`DECIMAL`和`BigDecimal`，不得使用`float`或`double` |
| 数据库脚本 | 模块脚本不包含`DROP DATABASE`、`CREATE DATABASE`或`USE` |

禁止提交真实密码、个人数据库配置、IDE私有配置、编译产物和本地日志。配置示例只保留占位值。

## 4 六张核心表的最小兼容结构

下面字段是跨模块最小接口。负责人可以增加字段，但不得删除、改名、改变含义或把类型缩小到不能兼容本文档的范围。

### 4.1 A负责的表

#### student

| 字段 | 类型 | 约束和语义 |
|---|---|---|
| `id` | `BIGINT` | 自增主键 |
| `student_no` | `VARCHAR(20)` | 非空、非空白、唯一 |
| `name` | `VARCHAR(50)` | 非空、非空白 |
| `major` | `VARCHAR(100)` | 非空、非空白 |

必须保留`(major, student_no)`索引，以支持按专业筛选并按学号排序。

#### enrollment

| 字段 | 类型 | 约束和语义 |
|---|---|---|
| `id` | `BIGINT` | 自增主键 |
| `student_id` | `BIGINT` | 非空，外键指向`student.id` |
| `teaching_class_id` | `BIGINT` | 非空，外键指向`teaching_class.id` |
| `status` | `VARCHAR(10)` | 非空、大小写敏感，只允许`ENROLLED`或`WITHDRAWN` |
| `selected_at` | `DATETIME(6)` | 非空，最近一次选入时间 |
| `withdrawn_at` | `DATETIME(6)` | 可空，最近一次退课时间 |
| `score` | `DECIMAL(5,2)` | 可空；非空时为0至100 |

冻结规则：

- `UNIQUE(student_id, teaching_class_id)`，同一学生和教学班只保留一条当前关系。
- `ENROLLED`记录的`withdrawn_at`必须为空。
- `WITHDRAWN`记录的`score`必须为空，且`withdrawn_at`不得早于`selected_at`。
- 重新选课更新原记录，刷新`selected_at`，清空`withdrawn_at`和`score`。
- 仅`ENROLLED`记录计入教学班人数和成绩统计。
- 仅`ENROLLED`记录允许录入或修改成绩。
- 已有成绩的记录不允许退课。
- 外键删除和更新策略为`RESTRICT`。

### 4.2 B必须提供的表

#### teacher

| 字段 | 最小类型 | 约束和语义 |
|---|---|---|
| `id` | `BIGINT` | 自增主键 |
| `teacher_no` | `VARCHAR(20)` | 非空、非空白、唯一 |
| `name` | `VARCHAR(50)` | 非空、非空白 |

#### course

| 字段 | 最小类型 | 约束和语义 |
|---|---|---|
| `id` | `BIGINT` | 自增主键 |
| `course_code` | `VARCHAR(20)` | 非空、非空白、唯一 |
| `name` | `VARCHAR(100)` | 非空、非空白 |
| `credits` | `DECIMAL(4,1)` | 非空且大于0，Java读取为`BigDecimal` |

B可以为教师或课程增加院系、职称等字段，但A、C不能依赖未写入本文档的新增字段。已被`teaching_class`引用的教师和课程不得直接删除。

### 4.3 C必须提供的表

#### semester

| 字段 | 最小类型 | 约束和语义 |
|---|---|---|
| `id` | `BIGINT` | 自增主键 |
| `name` | `VARCHAR(30)` | 非空、非空白、唯一 |
| `start_date` | `DATE` | 非空 |
| `end_date` | `DATE` | 非空且不早于`start_date` |

#### teaching_class

| 字段 | 最小类型 | 约束和语义 |
|---|---|---|
| `id` | `BIGINT` | 自增主键 |
| `class_code` | `VARCHAR(30)` | 非空、非空白、唯一 |
| `course_id` | `BIGINT` | 非空，外键指向`course.id` |
| `teacher_id` | `BIGINT` | 非空，外键指向`teacher.id` |
| `semester_id` | `BIGINT` | 非空，外键指向`semester.id` |
| `capacity` | `INT` | 非空且大于0 |

必须至少建立以下索引：

- `(semester_id, course_id)`，支持按学期和课程查询、统计。
- `(teacher_id)`，支持教师教学班查询。

已被`enrollment`引用的教学班不得直接删除。调整容量后，`capacity`不得小于该班当前`ENROLLED`人数。

## 5 表依赖和SQL执行顺序

建表文件名和顺序固定如下：

1. A：`database/member-a/01_create_student.sql`
2. B：`database/member-b/02_create_teacher_course.sql`
3. C：`database/member-c/03_create_semester_teaching.sql`，文件内先建`semester`，再建`teaching_class`
4. A：`database/member-a/04_create_enrollment.sql`

初始化文件名和顺序固定如下：

1. A：`database/member-a/11_seed_student.sql`
2. B：`database/member-b/12_seed_teacher_course.sql`
3. C：`database/member-c/13_seed_semester_teaching.sql`
4. A：`database/member-a/14_seed_enrollment.sql`

所有初始化脚本只允许在空测试库运行，不使用`INSERT IGNORE`、`REPLACE`或覆盖已有数据的方式隐藏编号冲突。

## 6 演示数据编号契约

为保证三人的初始化脚本能够独立编写并一次执行成功，第一批演示数据固定使用以下编号：

| 表 | 编号范围 | 负责人 |
|---|---|---|
| `student` | 1001至1010 | A |
| `teacher` | 1101至1110 | B |
| `course` | 1201至1210 | B |
| `semester` | 1301至1310 | C |
| `teaching_class` | 2001至2010 | C |
| `enrollment` | 3001至3010 | A |

附加规则：

- B、C必须保证所有外键引用的数据在后续脚本执行前已经存在。
- C的2001至2010教学班必须能够被A的初始化数据引用。
- 2001至2010每个教学班的初始容量不得小于3，方便演示正常选课、满员和最后名额竞争。
- 每张核心表至少提供10条有效测试数据。
- 如需增加数据，从各表下一个未使用编号开始，不修改其他成员已经引用的编号。

## 7 Java工程和接入契约

### 7.1 包和文件所有权

| 路径或文件 | 负责人 | 规则 |
|---|---|---|
| `app/pom.xml` | B | 统一JDK、MySQL驱动和构建配置 |
| `edu.project.Main` | B | 唯一正式程序入口 |
| `edu.project.common.DBUtil` | B | 唯一公共数据库连接实现 |
| `edu.project.student` | A | 学生、选课、成绩模块 |
| `edu.project.catalog` | B | 教师、课程模块 |
| `edu.project.teaching` | C | 学期、教学班、统计模块 |

成员发现他人文件的问题时，先提出修改需求，由文件负责人修改；不得在个人分支顺手重写他人的DAO、模型或脚本。

### 7.2 DBUtil接口

B提供以下稳定接口：

```java
package edu.project.common;

public final class DBUtil {
    public static java.sql.Connection getConnection() throws java.sql.SQLException;
}
```

`getConnection()`每次返回独立且`autoCommit=true`的连接。调用方负责关闭连接。配置至少使用`url`、`user`、`password`三个键；正式配置文件不得提交Git。

A现有模块按以下方式接入，不移动或重命名A的`ConnectionProvider`：

```java
new StudentMenu(DBUtil::getConnection).run(scanner);
```

B、C的菜单也必须公开`run(Scanner scanner)`。构造方式可以由各自实现决定，但必须能由`Main`注入或使用`DBUtil.getConnection()`，不得在DAO中写死URL、账号或密码。

### 7.3 Scanner和连接生命周期

- `Main`只创建一个`Scanner`并负责关闭。
- 三个子菜单不得关闭外部传入的`Scanner`。
- 普通DAO接受`Connection`参数，不自行提交或回滚。
- 普通单步CRUD使用独立连接；事务业务由Service统一创建连接和控制边界。
- 事务内所有DAO调用必须复用同一个`Connection`。
- JDBC资源统一使用`try-with-resources`关闭。
- 所有动态输入必须使用`PreparedStatement`，禁止字符串拼接用户输入。

## 8 事务和并发锁协议

所有涉及教学班容量或`enrollment`写入的事务统一使用`Connection.TRANSACTION_READ_COMMITTED`。

### 8.1 统一锁顺序

相关事务必须按照以下顺序执行：

1. 使用`SELECT ... FROM teaching_class WHERE id=? FOR UPDATE`锁定目标教学班。
2. 如需修改具体选课记录，再按学生和教学班查找并锁定`enrollment`记录。
3. 查询该班`status='ENROLLED'`的当前人数。
4. 完成业务校验和写入。
5. 提交；任一步失败则回滚。

任何模块不得反向先锁`enrollment`再锁`teaching_class`，以免与其他服务形成死锁。

### 8.2 写操作所有权

- 只有A的`EnrollmentService`和`GradeService`可以执行业务性的`enrollment`写入。
- B不得写入`enrollment`或`teaching_class`。
- C的名单和统计只读`enrollment`；C只能通过自己的容量调整Service修改`teaching_class.capacity`。
- 不允许通过原生SQL入口绕过服务直接选课、退课、录分或调整容量。

### 8.3 容量调整

C的容量调整事务必须：

1. 锁定目标`teaching_class`。
2. 统计当前`ENROLLED`人数。
3. 拒绝非正容量，或小于当前人数的新容量。
4. 更新容量并检查影响行数恰好为1。
5. 异常时完整回滚。

## 9 查询和统计口径

跨模块查询统一使用以下口径：

- “当前选课人数”只统计`enrollment.status='ENROLLED'`。
- 退课记录不进入人数、平均分、及格率和成绩单。
- “已出成绩人数”统计`ENROLLED`且`score IS NOT NULL`的记录。
- 平均分只对已出成绩的有效选课计算。
- 及格线固定为60；及格率分母为已出成绩人数，不是全部选课人数。
- 已出成绩人数为0时，平均分和及格率返回`NULL`或显示“暂无成绩”，不得除零或显示误导性的0分。
- `score=0`是有效成绩，不能当作未录入。
- 使用`LEFT JOIN`统计零选课教学班时，应计数`enrollment.id`，不得使用会把空连接行计为1的`COUNT(*)`。
- 复杂连接先确定目标粒度再聚合，避免因额外连接造成重复计数。

A的学生明细查询依赖以下固定列：

```text
student.student_no
student.name
enrollment.status
enrollment.score
teaching_class.class_code
teaching_class.course_id
course.name
course.credits
```

B、C不得重命名这些列。

## 10 错误处理与业务提示

- DAO抛出`SQLException`；Service将可预期的业务拒绝转换为明确的业务异常或返回值。
- 重复编号、非法外键、有关联数据的删除、满员、重复选课、非法成绩和容量不足必须给出可理解提示，不能使菜单直接退出。
- `INSERT`、`UPDATE`和`DELETE`必须检查影响行数。
- 事务捕获异常后必须回滚；回滚失败作为suppressed exception保留，不覆盖原始异常。
- 不向终端输出数据库密码或完整连接串中的敏感参数。

## 11 Git协作和合并规则

1. 三人只在自己的成员分支开发，通过Pull Request合入`main`。
2. 开始工作前同步最新`main`；提交前检查文件列表，不包含他人文件和本地产物。
3. 公共文件由指定负责人维护：B维护`pom.xml`、`DBUtil`、`Main`；C维护集成执行顺序、总README和最终汇总材料。
4. 跨模块字段或Java接口变更必须先更新本文档，再修改实现。
5. 不允许通过复制同一公共类到三个包来绕开接口协调。
6. 不允许为解决合并冲突而删除他人的业务逻辑；无法判断时由文件负责人处理。
7. 合并后其他成员必须同步`main`并重新编译自己的模块。

建议集成顺序：

1. 合并本文档并完成三人确认。
2. B合并公共Maven工程、`DBUtil`、`Main`及教师课程模块。
3. C合并学期教学班模块、正式外键表和执行顺序。
4. A合并学生选课成绩模块，或将现有A分支重新同步到最新`main`后发起合并。
5. C在集成分支生成统一脚本和总README，不改写三人的模块源文件。

## 12 联合测试和完成标准

在宣布集成完成前，三人必须共同完成以下检查：

- [ ] 在全新MySQL 8.4测试库按01、02、03、04顺序建表成功。
- [ ] 按11、12、13、14顺序初始化成功，每张核心表不少于10条记录。
- [ ] 在全新检出目录使用JDK 17和Maven编译成功。
- [ ] `Main`能够进入三个子菜单，返回主菜单后共享`Scanner`仍可使用。
- [ ] 学生、教师、课程、学期和教学班的负责功能可正常执行。
- [ ] A的学生CRUD、选课、退课、成绩录入或修改、明细和成绩单通过。
- [ ] B、C的查询使用本文档规定的字段和统计口径。
- [ ] 两个并发连接竞争最后一个名额时只有一个成功。
- [ ] 选课和容量调整同时发生时不超员，且不存在死锁或脏数据。
- [ ] 写入后、提交前模拟失败时事务完整回滚。
- [ ] 删除被引用的学生、课程、教师、学期或教学班时符合`RESTRICT`策略。
- [ ] 组内9条SQL查询和至少12个测试用例均有预期、实际结果和证据。
- [ ] 保存真实数据库和程序运行截图，日志不能替代课程要求的截图。
- [ ] 保存重要Prompt、模型回答、人工修改及至少两个真实AI错误的完整记录。

## 13 当前已知边界

本版本不实现登录鉴权、自动排课、先修课检查、时间冲突检查、同一课程不同教学班排他和完整选退事件历史。三人不得在报告中声称这些功能已经实现。

当前A模块已经按本文档中的`student`、`enrollment`、状态、统计和锁规则实现。B、C应先确认最小字段和事务协议，再开始写跨模块SQL。A用于独立测试的`course`和`teaching_class`只是夹具，不能作为B、C正式实现。
