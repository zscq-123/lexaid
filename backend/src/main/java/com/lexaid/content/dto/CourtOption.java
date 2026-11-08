package com.lexaid.content.dto;

import java.util.List;

public record CourtOption(String value, String label, List<CourtOption> children) {}
