package com.docmind.service;

import com.docmind.model.Document;
import com.docmind.repository.DocumentRepository;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IngestionService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final DocumentRepository documentRepository;

    public String ingest(MultipartFile file, String userId) throws Exception {

        // Step 1: Extract text using Tika (full class paths to avoid conflicts)
        org.apache.tika.Tika tika = new org.apache.tika.Tika();
        String rawText = tika.parseToString(file.getInputStream());

        // Step 2: Save metadata to PostgreSQL
        Document doc = new Document();
        doc.setFileName(file.getOriginalFilename());
        doc.setUserId(userId);
        doc.setUploadedAt(LocalDateTime.now());
        doc.setFileSizeBytes(file.getSize());
        documentRepository.save(doc);

        // Step 3: Split into chunks
        var splitter = DocumentSplitters.recursive(512, 50);

        dev.langchain4j.data.document.Document langDoc =
                dev.langchain4j.data.document.Document.from(rawText,
                        dev.langchain4j.data.document.Metadata.from("docId", doc.getId())
                                .put("fileName", file.getOriginalFilename())
                                .put("userId", userId));

        List<TextSegment> segments = splitter.split(langDoc);

        // Step 4: Embed and store in Qdrant
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);

        return doc.getId();
    }
}