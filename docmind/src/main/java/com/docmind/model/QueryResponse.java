package com.docmind.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;


@Data
    @AllArgsConstructor
    public class QueryResponse {
        private String answer;
        private List<String> sources;
}
