package edu.project.common;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** Loads a private UTF-8 properties file and opens a fresh connection per call. */
public final class DBUtil {
    private DBUtil() { }

    public static Connection getConnection() throws SQLException {
        Properties config = new Properties();
        try {
            Path path = Path.of(System.getProperty("db.config", "config/database.properties"));
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                config.load(reader);
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new SQLException("无法读取数据库配置，请检查 -Ddb.config 指定的 UTF-8 文件", "08001");
        }
        String url = config.getProperty("url");
        String user = config.getProperty("user");
        if (url == null || !url.startsWith("jdbc:mysql:") || user == null || user.isBlank()
                || !config.containsKey("password")) {
            throw new SQLException("数据库配置必须包含 MySQL url、非空 user 和 password", "08001");
        }
        // Do not forward unrelated properties, print the URL, or include credentials in errors.
        Properties credentials = new Properties();
        credentials.setProperty("user", user);
        credentials.setProperty("password", config.getProperty("password"));
        Connection connection = DriverManager.getConnection(url, credentials);
        try {
            connection.setAutoCommit(true);
            return connection;
        } catch (SQLException e) {
            try { connection.close(); } catch (SQLException closeError) { e.addSuppressed(closeError); }
            throw e;
        }
    }
}
