package com.docmind.service;

import com.docmind.model.QueryResponse;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Points;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
public class QueryService {

    private final EmbeddingModel embeddingModel;
    private final ChatLanguageModel chatLanguageModel;

    @Value("${qdrant.host}")
    private String qdrantHost;

    @Value("${qdrant.port}")
    private int qdrantPort;

    @Value("${qdrant.collection}")
    private String collection;

    public QueryResponse query(String question, String userId)
            throws ExecutionException, InterruptedException {

        // Step 1: Embed the question
        Embedding queryEmbedding = embeddingModel.embed(question).content();

        // Step 2: Search Qdrant directly
        QdrantClient qdrantClient = new QdrantClient(
                QdrantGrpcClient.newBuilder(qdrantHost, qdrantPort, false).build()
        );

        List<Float> vector = new ArrayList<>();
        for (float f : queryEmbedding.vector()) vector.add(f);

        List<Points.ScoredPoint> results = qdrantClient.searchAsync(
                Points.SearchPoints.newBuilder()
                        .setCollectionName(collection)
                        .addAllVector(vector)
                        .setLimit(5)
                        .setWithPayload(Points.WithPayloadSelector.newBuilder()
                                .setEnable(true).build())
                        .build()
        ).get();

        qdrantClient.close();

        if (results.isEmpty()) {
            return new QueryResponse("No relevant content found.", List.of());
        }

        // Step 3: Build context
        StringBuilder context = new StringBuilder();
        List<String> sources = new ArrayList<>();
        for (Points.ScoredPoint point : results) {
            String text = point.getPayloadMap().containsKey("text")
                    ? point.getPayloadMap().get("text").getStringValue() : "";
            String fileName = point.getPayloadMap().containsKey("fileName")
                    ? point.getPayloadMap().get("fileName").getStringValue() : "unknown";
            context.append("[").append(fileName).append("]\n")
                    .append(text).append("\n\n");
            sources.add(fileName);
        }

        // Step 4: Build prompt and call LLM
        String prompt = """
                Answer using ONLY the context below.
                Cite the source filename after your answer.

                Context:
                %s

                Question: %s
                """.formatted(context.toString(), question);

        String answer = chatLanguageModel.generate(prompt);
        return new QueryResponse(answer, sources.stream().distinct().toList());
    }
}