# A模块接口契约

状态：A实现采用的提案，等待B、C确认；并非三人已确认的接口。

## 环境和字段

目标MySQL 8.4 LTS、InnoDB、utf8mb4，Java 17。主键与跨模块外键统一为有符号BIGINT，Java long。数据库名由集成配置指定，模块脚本不执行CREATE DATABASE或USE。

| 表 | 字段和规则 |
|---|---|
| student | id BIGINT自增主键；student_no VARCHAR(20)非空唯一；name VARCHAR(50)非空；major VARCHAR(100)非空；字符串禁止空白 |
| enrollment | id BIGINT自增主键；student_id BIGINT非空；teaching_class_id BIGINT非空；status VARCHAR(10)非空，区分大小写，仅ENROLLED/WITHDRAWN；selected_at DATETIME(6)非空；withdrawn_at DATETIME(6)可空；score DECIMAL(5,2)可空 |

enrollment(student_id,teaching_class_id)唯一。外键删除/更新RESTRICT，保留业务记录；有历史选课的学生不能直接删除。成绩0—100且最多两位小数；WITHDRAWN必须无成绩且退课时间不早于选课时间，ENROLLED必须无退课时间。重新选课复用记录、更新selected_at、清空withdrawn_at。

## 依赖B和C

- C提供teaching_class(id BIGINT, class_code VARCHAR, course_id BIGINT, semester_id BIGINT, capacity INT)，capacity > 0；B提供course(id BIGINT, name VARCHAR, credits DECIMAL)。这些为A查询必需字段，尚待负责人确认。
- C的容量调整、A的选退课都先对目标教学班执行SELECT ... FOR UPDATE，再查询有效人数。全部事务采用READ COMMITTED以便等待锁之后读取新提交人数，且锁、计数、写入共用同一Connection。
- 成绩写入也采用相同教学班锁，防止退课与成绩录入交错；所有enrollment写操作由A的服务统一执行。原生SQL直接写入不能保证容量约束，应用账号不应绕过服务执行业务写入。
- 本版本只禁止同一教学班重复选课，允许选择同一课程的不同教学班；不实现先修课、时间冲突或同课程排他限制。若新增需求需三人确认。
- 时间采用数据库本地时间，相关列均DATETIME(6)。部署时统一数据库时区。

## Java接入

A不修改B所有的pom、DBUtil和Main。A提供函数式接口edu.project.student.ConnectionProvider，其open()返回新建且autoCommit=true的Connection；连接由A调用方关闭。

```java
new StudentMenu(DBUtil::getConnection).run(scanner);
```

构造器注入避免依赖尚未存在的DBUtil。Scanner由Main管理，子菜单不关闭。DAO不自行获取或关闭连接，不提交事务；Service拥有事务边界。StudentDAO负责CRUD；EnrollmentDAO负责选课记录与两条多表查询；EnrollmentService负责选退课；GradeService负责成绩。

## 数据编号和执行顺序

A演示student id为1001—1010；enrollment id为3001—3010，依赖C提供teaching_class id为2001—2010，并为这些班预留至少1个名额。该编号段待C确认。A脚本顺序：01_create_student → B/C建表 → 04_create_enrollment → 11_seed_student → B/C初始化 → 14_seed_enrollment。初始化仅在空测试库运行，不覆盖现有记录。

## 验收

B确认连接注入和Java 17编译；C确认字段、编号、容量锁协议及隔离级别；三人实测最后名额竞争及选课/容量调整并发。不在确认前把测试用依赖表当作B/C正式实现。
