package com.example.demo.ngram.similarity;

import java.util.Set;

public interface SimilarityStrategy {

    /**
     * 두 N그램 집합 간의 유사도를 계산합니다.
     *
     * @param queryNGrams 질의(query)의 N그램 집합
     * @param docNGrams   도큐먼트의 N그램 집합
     * @return 계산된 유사도 값 (0과 1 사이의 실수)
     */
    double calculate(Set<String> queryNGrams, Set<String> docNGrams);
}
