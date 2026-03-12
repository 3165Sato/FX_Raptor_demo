package com.example.fxraptor.backoffice.dto;

import java.util.List;

public record ListResponse<T>(
        List<T> items,
        int total
) {
}
