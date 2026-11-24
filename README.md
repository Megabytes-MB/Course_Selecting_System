# 高校学生选课与成绩管理系统

《数据库系统》课程大作业，使用 Java、JDBC 和 MySQL，实现学生、课程、教学班、选课与成绩管理。项目按业务模块由三名成员协作完成，并保留数据库设计、SQL、测试证据和 AI 辅助过程材料。

**当前分支为 `member-a/student-enrollment`，已提供成员 A 的学生与选课成绩模块，可通过独立入口编译、测试和演示。** B、C 的正式模块及公共 Maven 工程尚未集成，完整系统仍需联合验证。

## 分支与协作

| 分支 | 用途 |
|---|---|
| `main` | 默认分支；当前文件树为空，作为后续集成的基线 |
| `member-a/student-enrollment` | 成员 A：学生、选课与成绩 |
| `member-b/teacher-course` | 计划由成员 B 使用：教师、课程与公共 Java 工程 |
| `member-c/semester-teaching` | 计划由成员 C 使用：学期、教学班与数据库集成 |

本 README 位于 A 分支。B、C 分支名称为分工约定，不代表对应分支或模块已经创建。各成员在自己的分支开发，通过 Pull Request 审核后合入 `main`。

| 成员 | 核心表 | 业务与协作职责 |
|---|---|---|
| A | `student`、`enrollment` | 学生 CRUD、选退课、成绩录入与修改、个人成绩单及选课事务 |
| B | `teacher`、`course` | 教师与课程管理；公共 Maven 工程、`DBUtil`、`Main` |
| C | `semester`、`teaching_class` | 学期与教学班管理、容量调整、名单与统计；数据库及最终材料集成 |

跨模块字段、编号和事务协议以 [A 模块接口契约](contracts/student-enrollment.md) 为提案，需三人确认后固定。具体对接事项见 [协作交接清单](docs/member-a/05_协作交接.md)。

## 当前功能

- 学生信息添加、按 ID 查询、按专业查询、修改和删除。
- 教学班选课、退课与重新选课；重复选课和满员选课拒绝。
- 成绩录入和修改；个人有效选课明细、已出成绩的个人成绩单。
- 使用 `PreparedStatement` 执行参数化 SQL；事务内 DAO 复用同一连接。
- 数据库主外键、唯一约束、`CHECK` 约束及查询索引。
- 每张 A 核心表 10 条虚构初始化记录、4 条 SQL 查询、14 组自动检查。
- 中文输入输出采用 UTF-8，拒绝保存包含无法解码替换字符的学生信息。

当前命令行版本不含登录与角色权限鉴别，也未实现先修课、时间冲突检查或同课程不同教学班的排他选课。

## 技术环境

| 项目 | 要求或已验证版本 |
|---|---|
| Java | JDK 17 或以上；源码使用 `record` 和 `switch` 表达式 |
| 数据库目标 | MySQL 8.4 LTS、InnoDB、`utf8mb4` |
| 已验证数据库 | MySQL 8.0.45；目标 8.4 仍需复验 |
| JDBC 驱动 | 本轮使用 MySQL Connector/J 9.7.0 |
| 补充测试数据库 | H2 2.4.240，MySQL 兼容模式 |
| 启动环境 | Windows PowerShell 5.1 或 PowerShell 7 |
| 本轮编译环境 | Temurin JDK 21.0.10，使用 `javac --release 17` 编译 |

当前 A 分支尚无 `pom.xml`，独立脚本直接使用 `javac` 编译。Maven 不是本入口的编译前提；脚本可以从本机 Maven 缓存查找驱动，或通过 `-DriverJar` 显式指定 JDBC 驱动文件。

## 目录结构

```text
Course_Selecting_System/
├── README.md
├── contracts/
│   └── student-enrollment.md       # A 模块字段、接口和事务约定
├── database/member-a/              # 建表、初始化、查询和约束检查 SQL
├── app/src/main/java/edu/project/student/
│   ├── ConnectionProvider.java     # 连接提供接口
│   ├── StudentMenu.java            # 学生模块命令行菜单
│   ├── model/                     # 学生、选课及查询结果模型
│   ├── dao/                       # StudentDAO、EnrollmentDAO
│   └── service/                   # 选退课、成绩服务和事务管理
├── tests/member-a/
│   ├── run-checks.ps1              # 编译及 H2、MySQL、Menu 模式入口
│   ├── ModuleChecks.java           # 14 组自动检查
│   ├── StandaloneLauncher.java     # A 模块独立启动入口
│   └── database.properties.example
├── docs/member-a/                  # 需求、E-R、规范化、测试及交接说明
├── evidence/member-a/              # 实际运行日志及验证状态
└── ai-records/member-a/            # AI 过程、纠错和效果评价草稿
```

`tests/member-a/build/` 和真实的 `database.properties` 在本地生成，已加入 Git 忽略规则。

## 获取代码

```powershell
git clone --branch member-a/student-enrollment https://github.com/Megabytes-MB/Course_Selecting_System.git
Set-Location .\Course_Selecting_System
java -version
javac -version
```

以下命令均在仓库根目录执行。已有本地仓库的成员直接进入自己的仓库目录即可。

## 快速验证 H2

H2 检查不需要本机 MySQL 服务。先准备 H2 驱动；如果已安装 Maven，可以将依赖下载到本机缓存：

```powershell
mvn dependency:get "-Dartifact=com.h2database:h2:2.4.240"
```

运行检查：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tests\member-a\run-checks.ps1 -Mode H2
```

也可在上述命令末尾增加 `-DriverJar 'C:\drivers\h2-2.4.240.jar'`，使用自行下载的驱动。成功时末尾显示：

```text
RESULT: 14 / 14 passed; backend=H2 (supplement only)
```

H2 只提供补充验证。测试适配层移除 MySQL 专用的字符集和引擎声明，因此不能用 H2 结果代替真实 MySQL 验证。

## MySQL 建库与配置

### 1 创建独立空测试库

使用本机 MySQL 管理员账号，在 Workbench、Navicat 或 MySQL 命令行中执行。将示例密码替换成自己设置的测试密码：

```sql
CREATE DATABASE member_a_test_local
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'member_a_tester'@'localhost'
    IDENTIFIED BY 'REPLACE_WITH_YOUR_TEST_PASSWORD';

GRANT ALL PRIVILEGES
    ON member_a_test_local.*
    TO 'member_a_tester'@'localhost';

SHOW TABLES FROM member_a_test_local;
```

最后一条应返回空结果。库名必须以 `member_a_test_` 开头；如果示例库已存在，创建一个新名称，并同步修改授权和连接配置。不要使用已有业务库。

### 2 填写本地配置

首次使用时复制配置示例。已有配置则直接使用，避免覆盖：

```powershell
if (-not (Test-Path .\tests\member-a\database.properties)) {
    Copy-Item .\tests\member-a\database.properties.example .\tests\member-a\database.properties
}
```

编辑生成的 `database.properties`：

```properties
url=jdbc:mysql://localhost:3306/member_a_test_local?characterEncoding=UTF-8&connectionTimeZone=LOCAL
user=member_a_tester
password=REPLACE_WITH_YOUR_TEST_PASSWORD
```

端口、库名、账号和密码以本机实际设置为准，值不加引号。真实配置只保存在本地，不提交到 Git。

### 3 准备 MySQL 驱动并执行测试

如果使用 Maven 缓存准备驱动：

```powershell
mvn dependency:get "-Dartifact=com.mysql:mysql-connector-j:9.7.0"
```

然后运行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tests\member-a\run-checks.ps1 -Mode MySQL -ConfigPath .\tests\member-a\database.properties
```

没有缓存时，可在命令末尾增加 `-DriverJar 'C:\drivers\mysql-connector-j-9.7.0.jar'`。

**MySQL 测试入口要求目标库为空。** 它会创建 A 的两张表，以及最小 `course`、`teaching_class` 依赖夹具；随后在每组用例前重置这些表的数据。不要提前手动运行建表或初始化脚本。夹具仅用于独立测试，不代表 B、C 的正式表设计。

成功时末尾显示：

```text
RESULT: 14 / 14 passed; backend=MySQL
```

测试结束后保留演示数据。再次运行整套 MySQL 检查需要新的独立空库；已经初始化的库可直接用于下一节的菜单演示。日志保存在 `evidence/member-a/`。

## 启动命令行菜单

在数据库已通过测试初始化后执行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tests\member-a\run-checks.ps1 -Mode Menu -ConfigPath .\tests\member-a\database.properties
```

`-ExecutionPolicy Bypass` 只作用于这次启动的 PowerShell 进程，不修改系统或用户的永久执行策略。每次启动会重新编译 A 模块。

| 菜单编号 | 功能 |
|---|---|
| 1 | 添加学生 |
| 2 | 按学生 ID 查询 |
| 3 | 按专业查询并按学号排序 |
| 4 | 修改学生学号、姓名、专业 |
| 5 | 删除学生 |
| 6 | 选课 |
| 7 | 退课 |
| 8 | 成绩录入或修改 |
| 9 | 有效选课明细 |
| 10 | 已出成绩的个人成绩单 |
| 0 | 返回并结束独立菜单 |

可先选择 `2` 并输入学生 ID `1001`，再选择 `9` 或 `10` 查询同一学生。初始化数据中，学生 `1001` 有两条有效选课，其中一条成绩为 `90.50`，另一条尚未录入成绩；成绩单仅显示已出成绩记录。

## 业务规则与事务

- 学号唯一；姓名、学号和专业不可为空白。有选课记录的学生受外键限制，不能直接删除。
- 同一学生与同一教学班只保留一条关系，状态为 `ENROLLED` 或 `WITHDRAWN`。
- 仅 `ENROLLED` 计入教学班人数；退课保留记录，重新选课更新原记录及选课时间。
- 成绩可为空，非空时范围为 0—100，最多两位小数。只有未出成绩的已选记录允许退课。
- 选课、退课及成绩写入先锁定教学班记录，再检查并写入；事务使用 `READ COMMITTED`，失败执行 `rollback`。
- C 的容量调整服务需遵循同一教学班行锁协议。绕过服务直接写入 SQL，不能仅靠行级 `CHECK` 保证不超员。

## 正式 Java 工程接入

B 的 `Main` 可通过公共 `DBUtil` 注入连接：

```java
new StudentMenu(DBUtil::getConnection).run(scanner);
```

`DBUtil` 每次返回独立、`autoCommit=true` 的连接，由 A 模块关闭；上层 `Main` 管理 `Scanner` 生命周期，学生子菜单不关闭它。正式入口需同样统一终端输入编码，并使用 UTF-8 读取中文输入。DAO 不自行获取连接或提交事务，Service 管理事务边界。

## 验证记录与设计材料

截至已保存的 2026 年 11 月 20 日验证记录：

- MySQL 8.0.45、Connector/J 9.7.0：14 组自动检查通过，覆盖 CRUD、唯一与外键约束、成绩边界、重复和满员拒绝、退课重选、回滚及最后名额并发竞争。
- Windows PowerShell 5.1：脚本解析与菜单启动通过；编码修复后 H2 的 14 组回归检查通过。
- 中文输入验证：通过修复后启动器输入中文姓名和专业，写入后由独立 JDBC 查询逐字核对，结果一致。

详细材料：

- [需求分析](docs/member-a/01_需求分析.md)
- [概念设计与 E-R 图](docs/member-a/02_概念设计.md)及 [Mermaid 源码](docs/member-a/ER图.mmd)
- [逻辑设计与规范化分析](docs/member-a/03_逻辑设计与规范化.md)
- [测试报告](docs/member-a/04_测试报告.md)及 [当前验证状态](evidence/member-a/验证状态.md)
- [MySQL 自动检查日志](evidence/member-a/mysql-20261120-102925.log)
- [中文输入读回验证日志](evidence/member-a/chinese-input-roundtrip-20261120.log)
- [AI 过程与纠错记录](ai-records/member-a/过程与纠错记录.md)及 [效果评价草稿](ai-records/member-a/辅助效果评价草稿.md)

日志记录实际检查结果，最终报告仍需补充真实运行截图和学生本人审核。

## 常见问题

**PowerShell 报 `UnexpectedToken` 或 `-Dfile` 解析错误**

`run-checks.ps1` 含中文提示，应保存为 UTF-8 BOM，以兼容 Windows PowerShell 5.1。使用仓库内修复后的脚本，编辑时保留该编码。

**菜单正常，但新输入的姓名或专业乱码**

先退出旧菜单，再通过最新脚本重新启动。启动器已统一输入输出 UTF-8；含 `�` 替换字符的学生信息会被拒绝保存。已经入库的错误内容需要通过菜单 `4` 重新填写，替换字符无法自动恢复成原文。

**提示测试库不是空库**

如果先前检查已成功，直接使用 `-Mode Menu`。重跑完整检查时另建独立空测试库并修改配置，不要使用含已有业务数据的库。

**找不到驱动或无法连接 MySQL**

用 `-DriverJar` 指定实际驱动文件，检查 MySQL 服务、端口、库名和账号授权。不要将真实密码或整个私有配置上传到仓库。

## 后续集成

- 三人确认字段、主外键类型、演示编号、连接接口和容量锁协议。
- 合并 B 的公共工程、C 的正式教学班模块，使用完整表结构进行联合测试。
- 在目标 MySQL 8.4 LTS 环境复验，并补充容量调整与选课并发检查。
- 完善真实运行截图、最终 PDF 和学生审核后的 AI 辅助评价。
