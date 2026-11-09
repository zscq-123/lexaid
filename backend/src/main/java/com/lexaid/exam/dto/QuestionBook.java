package com.lexaid.exam.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record QuestionBook(@JsonIgnore Long questionId, String codeName, String bookName) {}
