package com.docmind.controller;

import com.docmind.model.QueryResponse;
import com.docmind.service.QueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/query")
@RequiredArgsConstructor
public class QueryController {

    private final QueryService queryService;

    @PostMapping
    public ResponseEntity<QueryResponse> ask(
            @RequestBody Map<String, String> body,
            @RequestHeader("X-User-Id") String userId) throws Exception {

        QueryResponse response =
                queryService.query(body.get("question"), userId);
        return ResponseEntity.ok(response);
    }
}