package com.example.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Milvus 向量存储服务
 *
 * 核心功能（纯向量操作，不涉及 LLM）：
 * 1. ingest() - 文档加载 → 分块 → 向量化 → 存入 Milvus
 * 2. search() - 查询文本 → 向量化 → 在 Milvus 中相似搜索 → 返回匹配文档
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final VectorStore vectorStore;

    @Value("classpath:data/knowledge-base.txt")
    private Resource knowledgeBaseResource;

    /**
     * 文档摄入：加载文档 → 分块 → 向量化 → 存入 Milvus
     *
     * Spring AI 自动处理 Embedding 调用，你只需要传入 Document 列表
     */
    public int ingest() {
        log.info(">>> 开始文档摄入...");

        // 1. 加载文档
        TextReader textReader = new TextReader(knowledgeBaseResource);
        List<Document> documents = textReader.get();
        log.info("加载了 {} 个文档", documents.size());

        // 2. 文档分块（TokenTextSplitter 按 token 数分块，避免超出 Embedding 模型的上下文限制）
        // Spring AI 2.0 推荐使用 builder 模式
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(500)           // 每个文本块的目标 token 数
                .withMinChunkSizeChars(350)   // 最小字符数
                .build();
        List<Document> chunks = splitter.apply(documents);
        log.info("分块后得到 {} 个文档块", chunks.size());

        // 3. 向量化 + 存储（Spring AI 自动调用 Embedding 模型并写入 Milvus）
        vectorStore.add(chunks);
        log.info("文档已向量化并存入 Milvus");

        return chunks.size();
    }

    /**
     * 向量相似搜索：查询文本 → 向量化 → 在 Milvus 中搜索 → 返回匹配文档
     *
     * @param query 查询文本
     * @param topK 返回最相似的 K 条结果
     * @return 匹配的文档列表（包含文本内容和相似度分数）
     */
    public List<Document> search(String query, int topK) {
        log.info(">>> 向量搜索: query={}, topK={}", query, topK);

        // Spring AI 自动将 query 向量化，然后在 Milvus 中搜索
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .build()
        );
        
        log.info("搜索到 {} 个相关文档", results.size());
        return results;
    }

    /**
     * 带相似度阈值的搜索
     *
     * @param query 查询文本
     * @param topK 返回最相似的 K 条结果
     * @param similarityThreshold 相似度阈值（0-1），低于此值的结果会被过滤
     * @return 匹配的文档列表
     */
    public List<Document> searchWithThreshold(String query, int topK, double similarityThreshold) {
        log.info(">>> 带阈值的向量搜索: query={}, topK={}, threshold={}", query, topK, similarityThreshold);

        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(similarityThreshold)
                        .build()
        );
        
        log.info("搜索到 {} 个相关文档（阈值: {}）", results.size(), similarityThreshold);
        return results;
    }

    /**
     * 格式化搜索结果，便于展示
     */
    public String formatResults(List<Document> results) {
        if (results.isEmpty()) {
            return "未找到相关内容";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(results.size()).append(" 条相关结果：\n\n");
        
        for (int i = 0; i < results.size(); i++) {
            Document doc = results.get(i);
            sb.append("【").append(i + 1).append("】\n");
            sb.append("内容: ").append(doc.getText()).append("\n");
            sb.append("元数据: ").append(doc.getMetadata()).append("\n");
            sb.append("---\n");
        }
        
        return sb.toString();
    }
}
