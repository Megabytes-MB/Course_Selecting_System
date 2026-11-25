package edu.project.catalog.dao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;

final class CatalogValues {
    private CatalogValues() { }

    static String text(String value, int max, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
        String result = value.strip();
        if (result.codePointCount(0, result.length()) > max)
            throw new IllegalArgumentException(label + "长度不能超过" + max);
        if (result.indexOf('\uFFFD') >= 0)
            throw new IllegalArgumentException("输入包含无法解码的字符，请使用 UTF-8 终端重新输入");
        return result;
    }

    static long id(long id) {
        if (id <= 0) throw new IllegalArgumentException("ID 必须为正整数");
        return id;
    }

    static BigDecimal credits(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.compareTo(new BigDecimal("999.9")) > 0)
            throw new IllegalArgumentException("学分必须在 0.1 至 999.9 之间");
        try { return value.setScale(1, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("学分最多保留一位有效小数"); }
    }

    static boolean affected(int count) throws SQLException {
        if (count < 0 || count > 1) throw new SQLException("写操作影响行数异常");
        return count == 1;
    }
}
