package com.example.demo.ngram.similarity;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 자카드 유사도를 계산하는 클래스입니다.
 * 자카드 유사도는 두 집합 간의 교집합 크기를 합집합 크기로 나눈 값으로 정의됩니다.
 */
@Component(SimilarityStrategies.JACCARD)
public class JaccardSimilarity implements SimilarityStrategy {

    @Override
    public double calculate(Set<String> queryNGrams, Set<String> docNGrams) {
        // 질의 또는 도큐먼트의 N그램 집합이 비어있는 경우 유사도는 0
        if (queryNGrams.isEmpty() || docNGrams.isEmpty()) {
            return 0.0;
        }

        // 교집합을 구함
        Set<String> intersection = new HashSet<>(queryNGrams);
        intersection.retainAll(docNGrams);

        // 합집합을 구함
        Set<String> union = new HashSet<>(queryNGrams);
        union.addAll(docNGrams);

        // 자카드 유사도 계산: 교집합 크기 / 합집합 크기
        return (double) intersection.size() / union.size();
    }
}
