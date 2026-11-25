# 成员 B 教师课程模块与运行说明

成员 B 已提供教师和课程两张表、各十条数据、三条查询、完整 CRUD、公共 Maven 工程、连接工具和 Main。本次工作基于 main 的 `96f266f` 创建 `member-b/teacher-course`，A/C 源码与共享基线保持原有版本。C 的正式实现合并后仍需全组联合验收。

## 交付索引

- [需求分析](01_需求分析.md)、[概念设计](02_概念设计.md)、[逻辑设计与规范化](03_逻辑设计与规范化.md)
- [测试报告](04_测试报告.md)、[协作交接](05_协作交接.md)
- [教师课程契约](../../contracts/teacher-course.md)、[公共 Java 接口](../../contracts/java-interface.md)
- [AI 过程及真实纠错](../../ai-records/member-b/过程与纠错记录.md)、[辅助效果评价草稿](../../ai-records/member-b/辅助效果评价草稿.md)
- [运行截图与证据说明](../../evidence/member-b/验证状态.md)

## 环境与构建

目标及本次实测：JDK 17.0.18、Maven 3.9.4、MySQL 8.4.7、MySQL Connector/J 9.7.0。驱动的运行依赖为 protobuf-java 4.31.1。没有使用 Spring。以下命令在仓库根目录执行。

```powershell
# 将此路径改为本机实际 JDK 17 路径
$env:JAVA_HOME = 'D:\develop\Java\jdk-17.0.18'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn -B -f .\app\pom.xml package
```

输出为 `app/target/course-selection-system-1.0-SNAPSHOT.jar` 和 `app/target/dependency/`。Maven package 只构建工程，数据库集成测试需另行执行，不能将构建阶段的“No tests to run”当成业务测试已通过。

## 正式数据库初始化

由 C 汇总建库与完整执行顺序。管理员先创建独立的空 MySQL 数据库（utf8mb4）并授权本地用户，再按 01/02/03/04、11/12/13/14 执行各成员正式脚本。B 只维护 `database/member-b/02_create_teacher_course.sql`、`12_seed_teacher_course.sql`、`queries.sql`、`constraint_checks.sql`。当前 main 缺少 C 正式 SQL，不能把 tests 下的夹具复制为生产脚本或声称完整正式建库已验收。

将 `app/config/database.properties.example` 复制为 `app/config/database.properties`，填写实际 url、user、password。真实配置已被现有 `.gitignore` 忽略，不提交。若已有配置，不覆盖。配置用 UTF-8 保存，路径可通过 JVM 参数指定。

## 启动正式主菜单

```powershell
[Console]::InputEncoding = [Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
$OutputEncoding = [Console]::OutputEncoding
$dbConfig = (Resolve-Path .\app\config\database.properties).Path
java '-Dfile.encoding=UTF-8' "-Ddb.config=$dbConfig" -cp '.\app\target\classes;.\app\target\dependency\*' edu.project.Main
```

主菜单 1 为 A 的学生选课模块，2 为 B 的教师课程，3 为 C 的学期教学班。C 缺席时提示尚未集成并返回。B 菜单 1—5 为教师增查列改删，6—10 为课程增查列改删，11 为按学分查询，12 为教师教学班，13 为按学期查课，0 返回主菜单。主菜单 0 退出。

B 初始化中，可先查询课程 1201，再使用最低学分 3.0 验证六门课程。教师教学班、指定学期开课课程需要 C 表，当前独立测试库使用 B 测试夹具完成演示。

## 可重复的独立数据库验证

先由管理员创建新的空库，库名以 `member_b_test_` 开头，并给测试用户授予该库内的建表和读写权限；不要授予其他业务库权限。另存私有配置指向该库，然后运行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tests\member-b\run-checks.ps1 `
  -ConfigPath 'D:\private\member-b-database.properties' -JavaHome $env:JAVA_HOME
```

脚本先 Maven 构建，再编译、执行 `ModuleChecks`，本次结果为 `RESULT: 24 / 24 passed; backend=MySQL; C dependencies=TEST FIXTURE ONLY`。它拒绝非 MySQL、非测试前缀或非空库，不删除或重置既有数据。测试创建 B 正式表、A 正式表和 `tests/member-b/fixtures/cross-module.sql` 中的 C 依赖夹具；各六表十条记录只证明夹具环境相容。再次全量运行应另建空库。`--existing` 仅供复核已由该工具初始化的独立测试库，不用于普通数据库。

## 本机验证环境与依赖位置

本轮下载 MySQL 8.4.7 Windows ZIP 至 `D:\candelete\member-b-tools\mysql-8.4.7-winx64.zip`，解压于同目录下的 `mysql-8.4.7-winx64`。独立数据目录是 `D:\candelete\member-b-tools\mysql-data`，监听 `127.0.0.1:33084`，未注册 Windows 服务，未更改现有 MySQL80。私有配置 `D:\candelete\member-b-tools\database-final.properties` 指向 `member_b_test_final_20261125`，密码不在文档中披露。

JDK 17 和 Maven 为本机已有工具。Connector/J 9.7.0 复用已有 Maven 缓存，新增 protobuf-java 4.31.1、Maven dependency plugin 3.7.1 等由 Maven 解析的依赖位于本机配置的 `D:\develop\apache-maven-3.9.4\mvn_repo`；运行 JAR 副本位于 `app/target/dependency`，构建产物不提交。

如本轮临时 MySQL 已停止，可在 PowerShell 启动已存在的数据目录：

```powershell
$mysqlBase = 'D:\candelete\member-b-tools\mysql-8.4.7-winx64'
$mysqlData = 'D:\candelete\member-b-tools\mysql-data'
Start-Process -FilePath "$mysqlBase\bin\mysqld.exe" -WindowStyle Hidden -ArgumentList @(
  "--basedir=$mysqlBase", "--datadir=$mysqlData", '--port=33084',
  '--bind-address=127.0.0.1', '--mysqlx=OFF',
  '--log-error=D:\candelete\member-b-tools\mysql-runtime.log'
)
```

不要在同一端口或数据目录启动第二个实例；只有确认该临时实例停止后才使用上述命令。这是本机验收环境说明，不应硬编码到项目 DAO 或公共配置。

## 故障处理

无法读取配置时检查路径、编码和三项键，避免通过聊天或截图披露密码。驱动缺失时重新执行 Maven package，并保留启动 classpath 的 `dependency/*`；不能直接用不含依赖的普通 JAR 执行 `java -jar`。缺少教学班表时按正式集成顺序建库，独立验证则使用专属测试入口。PowerShell 控制台出现乱码时重设上述 UTF-8 参数，并检查配置文件编码。

重复编号、外键引用和非法学分应由菜单给出受控提示；数据库服务不可达时不展示原始连接异常。未变化的更新仍应视为记录存在，已覆盖驱动 affected-row 模式。
