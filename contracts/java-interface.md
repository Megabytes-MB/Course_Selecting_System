# 公共 Java 接口与启动契约

本文由成员 B 维护，明确 JDK 17、JDBC 连接及三个菜单的接入方式，供 A、C 审核。实际验证环境为 JDK 17.0.18、Maven 3.9.4、MySQL 8.4.7、Connector/J 9.7.0。

## 公共连接

```java
public static java.sql.Connection edu.project.common.DBUtil.getConnection()
    throws java.sql.SQLException;
```

每次调用返回独立且 autoCommit=true 的连接，由调用方关闭。配置默认相对于工作目录读取 `config/database.properties`；建议从 app 目录启动，或使用 JVM 参数 `-Ddb.config=绝对路径`。文件以 UTF-8 读取，必须包含 MySQL `url`、非空 `user`、`password` 三个键；密码可以显式为空，但示例不含真实凭据。

DBUtil 不缓存连接；不在日志或终端打印配置。缺失、无法解析或非法路径统一抛出 SQLState 08001 的 SQLException。DAO 向上抛 SQLException，业务菜单仅显示受控提示和 SQLState/错误码，不打印驱动原始错误或堆栈。

## 主入口与菜单

正式入口为 `edu.project.Main`。Main 创建唯一 UTF-8 Scanner 并在最终退出时关闭；子菜单不得关闭它。stdout/stderr 使用 UTF-8；Windows 启动器还应统一终端输入输出编码。启动前不强制连接，便于无配置时查看菜单。

```java
new edu.project.student.StudentMenu(DBUtil::getConnection).run(scanner);
new edu.project.catalog.CatalogMenu().run(scanner);
// C 按已公布契约提供：
new edu.project.teaching.TeachingMenu().run(scanner);
```

主线暂无 C 源码，因此 Main 对 C 使用固定类名的反射加载，要求 public 无参构造器、public 实例方法 `run(Scanner)`。类不存在时明确提示并返回；不创建冒充 C 的占位源码。C 合并后即可自动接入，运行失败或接口不兼容时给出提示。C 的接口调用仍需正式联调。

## 资源与事务

单步 CRUD 由 Service 打开一个连接，用 try-with-resources 关闭。DAO 仅管理自身 Statement/ResultSet。需要事务时由对应 Service 统一设置隔离级别、commit/rollback，所有 DAO 复用同一连接。A/C 涉及教学班和选课的事务沿用基线 READ COMMITTED 及先锁教学班的规则。

Maven 的 `package` 编译 Java 17 源码，产生普通应用 JAR，并将运行依赖复制到 `target/dependency`。该 JAR 不包含依赖，启动采用 classpath，详见 B 的运行说明。`mvn package` 的成功不代表集成检查执行；B 的数据库测试需另运行 `tests/member-b/run-checks.ps1`。
