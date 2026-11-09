package com.lexaid.exam.dto;

/** Null bookName denotes the distinct-question subtotal for the code, including multi-book questions once. */
public record CodeBookCount(String discipline, String subject, String codeName, String bookName,
                            Integer difficulty, long count) {}
