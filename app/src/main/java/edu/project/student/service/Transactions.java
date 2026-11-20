package edu.project.student.service;

import edu.project.student.ConnectionProvider;
import java.sql.*;

final class Transactions {
    @FunctionalInterface interface Work<T> { T run(Connection c) throws SQLException; }
    static <T> T execute(ConnectionProvider provider, Work<T> work) throws SQLException {
        try (Connection c = provider.open()) {
            if (!c.getAutoCommit()) throw new SQLException("连接提供方必须返回独立的autoCommit=true连接");
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException | Error failure) {
                try { c.rollback(); } catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                throw failure;
            }
            // Connection属于本次调用，直接关闭；不把未重置状态的连接交还调用者。
        }
    }
}
