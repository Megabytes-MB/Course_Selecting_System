# 成员A模块说明

本模块负责学生与选课成绩。面向MySQL 8.4 LTS和Java 17，通过JDBC实现，不依赖Spring。源代码位于app/src/main/java/edu/project/student。当前公共工程尚未由B建立，因此提供A独立编译和测试入口；没有代写B/C正式模块。

## 已提供的成果

- student、enrollment建表脚本、每表10条虚构数据、4条查询和约束检查说明。
- StudentDAO完整CRUD；EnrollmentDAO选课持久化、选课明细和成绩单查询。
- EnrollmentService选退课事务；GradeService成绩录入/修改；StudentMenu命令行菜单。
- 需求、E-R图、关系模式、函数依赖和3NF分析、测试用例、AI辅助过程记录。
- 可复现Java 17编译和14组自动检查；H2补充检查与真实MySQL验证明确区分。

## 快速验证

需JDK 17或以上和PowerShell。测试脚本优先从本机Maven缓存查找JDBC驱动，也可传入-DriverJar。没有缓存时自行下载对应驱动，不要求改B的pom。

```powershell
# 从course-selection-system目录执行
./tests/member-a/run-checks.ps1 -Mode H2
# 显式指定H2驱动（本轮使用2.4.240）
./tests/member-a/run-checks.ps1 -Mode H2 -DriverJar 'C:/path/h2-2.4.240.jar'
```

H2仅用于补充验证，测试适配层移除了MySQL专用字符集/引擎声明；实际产品脚本不因此改写。测试中的course、teaching_class为最小依赖夹具，不是B/C的完整数据库设计。

## MySQL实际验证

请在本机MySQL创建一个独立空测试库，例如member_a_test_local，并为测试账号授予该库建表、读写权限。不要使用已有业务库。将tests/member-a/database.properties.example复制到Git之外的私有位置填写配置，不要把密码粘贴到AI对话。

```powershell
./tests/member-a/run-checks.ps1 -Mode MySQL -ConfigPath 'C:/private/member-a-test.properties'
# 完成测试后，测试库保留最后一组用例的种子数据，可演示菜单。
./tests/member-a/run-checks.ps1 -Mode Menu -ConfigPath 'C:/private/member-a-test.properties'
```

测试入口校验库名前缀并检查空库，随后创建A表及依赖夹具，每组用例重置该测试库的数据。运行第二次需换新的独立空库，或者由用户自行清理这个专用测试库。脚本不会创建、删除数据库或操作其他库。真实MySQL运行会使用原始建表脚本，包括CHECK、外键及InnoDB声明。2026年11月20日在MySQL 8.0.45上14/14组检查通过，并验证实际菜单查询；本轮实际使用的MySQL驱动为Connector/J 9.7.0，B应在最终pom固定实际采用的驱动版本。

## 正式集成

1. B、C确认contracts/student-enrollment.md中的字段、Java接口、状态、锁和编号。
2. B提供公共Maven工程及DBUtil；在Main中调用new StudentMenu(DBUtil::getConnection).run(scanner)。A的源码全部为Java 17语法（record、switch表达式）。
3. C按student → teacher/course → semester/teaching_class → enrollment顺序建表；初始化按11、12、13、14顺序。
4. 使用三人正式表和数据重新运行查询、事务、并发与菜单测试，补充MySQL运行截图。
5. C汇总最终PDF和全组材料，A校对自身章节。当前Markdown设计材料可供审阅，尚未导出最终报告PDF。

所有enrollment写操作必须走统一服务；直接SQL写入无法单靠CHECK约束防止超员。测试故障通过连接代理在业务写入后、commit前抛出异常，不向正式服务添加后门。

## 当前限制

真实MySQL 8.0.45验证已完成，日志见evidence/member-a/mysql-20261120-102925.log及mysql-menu-20261120.log；目标MySQL 8.4 LTS尚需复验；B/C契约待确认；Git仓库及origin已配置，成果提交到member-a/student-enrollment分支；AI记录不能代替学生本人审核。截图必须来自实际运行，当前日志只是自动检查证据。


## Windows PowerShell 5.1启动兼容性

run-checks.ps1包含中文提示，以UTF-8 BOM保存，避免Windows PowerShell 5.1按系统ANSI代码页读取而出现UnexpectedToken。编辑该文件时保留UTF-8 BOM。2026年11月20日已用Windows PowerShell 5.1验证：解析错误为0，H2的14组检查通过，现有MySQL菜单的学生查询、选课明细和成绩单查询通过。

若本机默认策略禁止执行脚本，可以仅为本次子进程指定执行策略，不修改用户或系统策略：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tests\member-a\run-checks.ps1 -Mode Menu -ConfigPath .\tests\member-a\database.properties
```
## 中文输入编码修复 2026年11月20日

用户手动新增后，ID1013姓名和专业出现替换字符。JDBC只读核实两个字段均已存入U+FFFD，故影响数据内容，不只是字体显示。此前菜单查询的中文输出验证并未覆盖真实键盘中文输入，不能据此断言输入链路正常。

修复：run-checks.ps1同时设置Console.InputEncoding和OutputEncoding为UTF-8，保留UTF-8 BOM；StandaloneLauncher显式使用Scanner(System.in, StandardCharsets.UTF_8)；StudentDAO阻止包含U+FFFD的值入库并提示重新输入。B的正式Main也需统一输入编码。

验证：Windows PowerShell 5.1启动修复后的脚本，经UTF-8重定向输入新增“中文测试/输入输出验证”，按专业查询后，再由独立JDBC读回逐字比对，两字段均准确。临时验证记录已清理，用户ID1013保持原样；完整输出见evidence/member-a/chinese-input-roundtrip-20261120.log。H2的14组回归检查再次通过，A02新增替换字符拒绝断言。实际键盘输入仍请用户重启菜单后确认。

处理已有错误记录：关闭旧菜单，用修复后的脚本重新启动，选择4，输入ID1013、原学号及正确姓名和专业；已变为U+FFFD的原始内容无法从数据库自动还原。
