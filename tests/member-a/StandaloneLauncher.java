package edu.project.student;

import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.sql.*;
import java.nio.charset.StandardCharsets;

/** A-only launcher; B's Main/DBUtil remain their owner's responsibility. */
public final class StandaloneLauncher {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("请传入本地database.properties路径");
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of(args[0]))) { p.load(reader); }
        ConnectionProvider connections = () -> DriverManager.getConnection(p.getProperty("url"),p.getProperty("user"),p.getProperty("password"));
        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) { new StudentMenu(connections).run(scanner); }
    }
}
