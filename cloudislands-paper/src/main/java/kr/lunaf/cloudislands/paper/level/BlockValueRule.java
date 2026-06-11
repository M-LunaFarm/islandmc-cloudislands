package kr.lunaf.cloudislands.paper.level;

import java.math.BigDecimal;

public record BlockValueRule(String materialKey, BigDecimal worth, long levelPoints, int limit) {}
