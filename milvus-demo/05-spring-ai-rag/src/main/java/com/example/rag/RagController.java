package com.example.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Milvus 向量搜索 REST API
 * 
 * 纯向量操作，不涉及 LLM：
 * 1. POST /api/milvus/ingest - 文档向量化并存入 Milvus
 * 2. GET /api/milvus/search - 向量相似搜索
 */
@RestController
@RequestMapping("/api/milvus")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    /**
     * 文档摄入接口
     * 将 knowledge-base.txt 中的文档加载、分块、向量化后存入 Milvus
     */
    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingest() {
        int chunkCount = ragService.ingest();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "文档摄入完成",
                "chunkCount", chunkCount
        ));
    }

    /**
     * 向量相似搜索接口
     * 
     * @param query 查询文本
     * @param topK 返回最相似的 K 条结果（默认 5）
     * @return 匹配的文档列表
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {
        
        List<Document> results = ragService.search(query, topK);
        String formatted = ragService.formatResults(results);
        
        return ResponseEntity.ok(Map.of(
                "query", query,
                "topK", topK,
                "resultCount", results.size(),
                "results", results.stream().map(doc -> Map.of(
                        "content", doc.getText(),
                        "score", doc.getMetadata().getOrDefault("distance", 0.0),
                        "metadata", doc.getMetadata()
                )).toList(),
                "formatted", formatted
        ));
    }

    /**
     * 带相似度阈值的搜索接口
     * 
     * @param query 查询文本
     * @param topK 返回最相似的 K 条结果
     * @param threshold 相似度阈值（0-1）
     * @return 匹配的文档列表（过滤掉低于阈值的结果）
     */
    @GetMapping("/search/threshold")
    public ResponseEntity<Map<String, Object>> searchWithThreshold(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(defaultValue = "0.7") double threshold) {
        
        List<Document> results = ragService.searchWithThreshold(query, topK, threshold);
        
        return ResponseEntity.ok(Map.of(
                "query", query,
                "topK", topK,
                "threshold", threshold,
                "resultCount", results.size(),
                "results", results.stream().map(doc -> Map.of(
                        "content", doc.getText(),
                        "score", doc.getMetadata().getOrDefault("distance", 0.0),
                        "metadata", doc.getMetadata()
                )).toList()
        ));
    }
}
