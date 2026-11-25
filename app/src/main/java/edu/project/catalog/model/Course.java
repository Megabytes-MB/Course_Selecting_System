package edu.project.catalog.model;

import java.math.BigDecimal;

public record Course(long id, String courseCode, String name, BigDecimal credits) { }
