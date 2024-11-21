package com.example.demo.ngram.similarity;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 코사인 유사도를 계산하는 클래스입니다.
 * 코사인 유사도는 두 벡터 간의 코사인 각도를 사용하여 유사도를 측정합니다.
 */
@Component(SimilarityStrategies.COSINE)
public class CosineSimilarity implements SimilarityStrategy {

    @Override
    public double calculate(Set<String> queryNGrams, Set<String> docNGrams) {
        // 모든 토큰을 합쳐 전체 벡터 공간을 정의
        Set<String> allTokens = new HashSet<>();
        allTokens.addAll(queryNGrams);
        allTokens.addAll(docNGrams);

        // 질의와 도큐먼트의 벡터를 생성
        // 각 토큰이 존재하면 1, 존재하지 않으면 0으로 표시
        Map<String, Integer> queryVector = allTokens.stream()
                .collect(Collectors.toMap(
                        token -> token,
                        token -> queryNGrams.contains(token) ? 1 : 0
                ));

        Map<String, Integer> docVector = allTokens.stream()
                .collect(Collectors.toMap(
                        token -> token,
                        token -> docNGrams.contains(token) ? 1 : 0
                ));

        // 두 벡터의 내적을 계산
        int dotProduct = queryVector.keySet().stream()
                .mapToInt(token -> queryVector.get(token) * docVector.get(token))
                .sum();

        // 벡터의 크기를 계산
        double queryMagnitude = Math.sqrt(queryVector.values().stream()
                .mapToInt(value -> value * value)
                .sum());

        double docMagnitude = Math.sqrt(docVector.values().stream()
                .mapToInt(value -> value * value)
                .sum());

        // 벡터의 크기가 0인 경우 유사도는 0
        if (queryMagnitude == 0 || docMagnitude == 0) {
            return 0.0;
        }

        // 코사인 유사도 계산: 내적 / (질의 벡터 크기 * 도큐먼트 벡터 크기)
        return dotProduct / (queryMagnitude * docMagnitude);
    }
}
