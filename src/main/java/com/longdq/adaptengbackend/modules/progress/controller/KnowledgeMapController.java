package com.longdq.adaptengbackend.modules.progress.controller;

import com.longdq.adaptengbackend.modules.progress.dto.KnowledgeMapResponse;
import com.longdq.adaptengbackend.modules.progress.service.KnowledgeMapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/knowledge-map")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class KnowledgeMapController {

    private final KnowledgeMapService knowledgeMapService;

    @GetMapping
    public ResponseEntity<KnowledgeMapResponse> getKnowledgeMap() {
        return ResponseEntity.ok(knowledgeMapService.getKnowledgeMap());
    }
}