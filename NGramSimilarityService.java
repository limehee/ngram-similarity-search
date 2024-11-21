package com.example.demo.ngram;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.example.demo.ngram.similarity.SimilarityStrategies;
import com.example.demo.ngram.similarity.SimilarityStrategy;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * N-Gram 유사도 계산 및 캐싱을 담당하는 서비스 클래스입니다.
 */
@Service
public class NGramSimilarityService {

    // 캐시 설정을 위한 상수
    private static final int MAXIMUM_QUERY_CACHE_SIZE = 1_000;
    private static final int EXPIRE_AFTER_WRITE_QUERY_MINUTES = 10;
    private static final int MAXIMUM_DOCUMENT_CACHE_SIZE = 10_000;
    private static final int EXPIRE_AFTER_WRITE_DOCUMENT_MINUTES = 10;

    private final NGramParser nGramParser;

    private final SimilarityStrategy defaultStrategy;
    private final Map<String, SimilarityStrategy> strategyMap;

    // Caffeine Cache 설정
    private final Cache<String, Set<String>> queryNGramCache;
    private final Cache<String, Double> documentSimilarityCache;

    public NGramSimilarityService(
            NGramParser nGramParser,
            Map<String, SimilarityStrategy> strategyMap
    ) {
        this.nGramParser = nGramParser;
        this.defaultStrategy = strategyMap.get(SimilarityStrategies.COSINE);
        this.strategyMap = strategyMap;

        this.queryNGramCache = Caffeine.newBuilder()
                .maximumSize(MAXIMUM_QUERY_CACHE_SIZE)
                .expireAfterWrite(EXPIRE_AFTER_WRITE_QUERY_MINUTES, TimeUnit.MINUTES)
                .build();

        this.documentSimilarityCache = Caffeine.newBuilder()
                .maximumSize(MAXIMUM_DOCUMENT_CACHE_SIZE)
                .expireAfterWrite(EXPIRE_AFTER_WRITE_DOCUMENT_MINUTES, TimeUnit.MINUTES)
                .build();
    }

    /**
     * 검색어의 N-Gram을 생성하고 캐싱합니다.
     *
     * @param query 검색어
     * @param n     N-Gram의 n 값
     * @return 생성된 N-Gram 집합
     */
    public Set<String> getQueryNGrams(String query, int n) {
        String normalizedQuery = query.trim().toLowerCase();
        String queryCacheKey = normalizedQuery + "_" + n;
        return queryNGramCache.get(queryCacheKey, key -> nGramParser.generateNGrams(query, n));
    }

    /**
     * 유사도 계산을 수행하고 캐싱합니다.
     *
     * @param docId        문서 ID
     * @param field        필드명
     * @param queryNGrams  검색어의 N-Gram 집합
     * @param docNGrams    문서의 N-Gram 집합
     * @param strategyName 사용할 전략 이름 (null일 경우 기본 전략 사용)
     * @return 계산된 유사도 점수
     */
    public double calculateSimilarity(String docId, String field, Set<String> queryNGrams, Set<String> docNGrams, String strategyName) {
        SimilarityStrategy strategy = getStrategy(strategyName);
        String queryNGramKey = String.join(",", queryNGrams);
        String cacheKey = docId + "_" + field + "_" + strategy.getClass().getSimpleName() + "_" + queryNGramKey;

        return documentSimilarityCache.get(cacheKey, key -> strategy.calculate(queryNGrams, docNGrams));
    }

    /**
     * 사용자가 요청한 전략을 가져옵니다. 없으면 기본 전략을 반환합니다.
     *
     * @param strategyName 전략 이름
     * @return 선택된 전략
     */
    private SimilarityStrategy getStrategy(String strategyName) {
        if (strategyName == null || !strategyMap.containsKey(strategyName)) {
            return defaultStrategy;
        }
        return strategyMap.get(strategyName);
    }
}
