package com.delena.tradingportal.model;

/** Entry zone backing a decision (OB/FVG/composite band). Shared by decision + journal. */
public record Entry(String type, double low, double high) {
}
