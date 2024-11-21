package com.example.demo.ngram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class NGramSearchService {

    private final NGramRepository nGramRepository;
    private final ApplicationContext applicationContext;
    private final NGramSimilarityService nGramSimilarityService;

    /**
     * 여러 필드에 대해 N-Gram 검색을 수행하고, 검색된 원본 도큐먼트를 유사도에 따라 정렬하여 반환합니다.
     *
     * @param documentClass 도큐먼트 클래스 타입
     * @param fields        검색 대상 필드 리스트
     * @param query         검색어
     * @param <T>           도큐먼트 타입
     * @return 검색된 원본 도큐먼트 리스트
     */
    public <T> List<T> searchByNGram(Class<T> documentClass, List<String> fields, String query, String strategyName) {
        Map<String, Double> documentSimilarityScores = new HashMap<>();

        for (String field : fields) {
            try {
                Field targetField = documentClass.getDeclaredField(field);
                if (!targetField.isAnnotationPresent(NGramField.class)) {
                    throw new IllegalArgumentException(String.format("Field '%s' is not annotated with @NGramField", field));
                }

                NGramField annotation = targetField.getAnnotation(NGramField.class);
                int n = annotation.value();

                // 검색어 N-Gram 생성 및 캐싱
                Set<String> queryNGrams = nGramSimilarityService.getQueryNGrams(query, n);

                // N-Gram 검색 결과 조회
                List<NGramDocument> results = nGramRepository.findByCollectionNameAndFieldAndNgramsInAndN(
                        documentClass.getSimpleName(), field, new ArrayList<>(queryNGrams), n);

                // 문서별 유사도 계산
                calculateSimilarityScores(strategyName, field, results, queryNGrams, documentSimilarityScores);

            } catch (Exception e) {
                log.error("Failed to search by NGram for field '{}'", field, e);
                throw new RuntimeException(String.format("Failed to search by NGram for field '%s'", field), e);
            }
        }

        // 유사도에 따라 정렬하여 반환
        return fetchAndSortDocuments(documentClass, documentSimilarityScores);
    }

    /**
     * NGram 도큐먼트 리스트에 대해 유사도를 계산하고, 주어진 유사도 점수 맵에 값을 갱신합니다.
     *
     * @param strategyName             유사도 계산에 사용할 전략 이름
     * @param field                    처리 중인 필드 이름
     * @param results                  리포지토리에서 조회한 NGram 도큐먼트 리스트
     * @param queryNGrams              검색어로부터 생성된 N-Gram 집합
     * @param documentSimilarityScores 각 도큐먼트의 유사도 점수를 저장하거나 갱신할 맵
     */
    private void calculateSimilarityScores(String strategyName, String field, List<NGramDocument> results, Set<String> queryNGrams, Map<String, Double> documentSimilarityScores) {
        for (NGramDocument nGramDoc : results) {
            String docId = nGramDoc.getDocumentId();

            Set<String> docNGrams = new HashSet<>(nGramDoc.getNgrams());

            // 유사도 계산 및 캐싱
            double similarity = nGramSimilarityService.calculateSimilarity(docId, field, queryNGrams, docNGrams, strategyName);

            documentSimilarityScores.merge(docId, similarity, Double::max);
        }
    }

    /**
     * 원본 도큐먼트를 조회하고, 유사도 점수에 따라 내림차순으로 정렬합니다.
     *
     * @param documentClass            조회할 도큐먼트 클래스 타입
     * @param documentSimilarityScores 각 도큐먼트의 유사도 점수를 저장한 맵
     * @param <T>                      도큐먼트 클래스의 타입
     * @return 유사도 점수에 따라 정렬된 원본 도큐먼트 리스트
     */
    private <T> List<T> fetchAndSortDocuments(Class<T> documentClass, Map<String, Double> documentSimilarityScores) {
        Set<String> documentIds = documentSimilarityScores.keySet();
        List<T> documents = fetchOriginalDocuments(documentClass, documentIds);

        documents.sort((d1, d2) -> compareDocuments(d1, d2, documentSimilarityScores));

        return documents;
    }

    /**
     * 두 도큐먼트를 비교하여 유사도 점수에 따라 정렬 순서를 반환합니다.
     *
     * @param d1                       첫 번째 도큐먼트
     * @param d2                       두 번째 도큐먼트
     * @param documentSimilarityScores 각 도큐먼트의 유사도 점수를 저장한 맵
     * @param <T>                      도큐먼트 클래스의 타입
     * @return 유사도 점수에 따라 내림차순 정렬 (-1, 0, 1 중 하나)
     */
    private <T> int compareDocuments(T d1, T d2, Map<String, Double> documentSimilarityScores) {
        String id1 = getDocumentId(d1);
        String id2 = getDocumentId(d2);
        Double score1 = documentSimilarityScores.getOrDefault(id1, 0.0);
        Double score2 = documentSimilarityScores.getOrDefault(id2, 0.0);
        return score2.compareTo(score1); // 내림차순 정렬
    }

    /**
     * 도큐먼트의 ID를 추출합니다.
     *
     * @param document 대상 도큐먼트 객체
     * @return 도큐먼트의 ID 값 (문자열로 변환)
     */
    private String getDocumentId(Object document) {
        try {
            Field idField = Stream.of(document.getClass().getDeclaredFields())
                    .filter(field -> field.isAnnotationPresent(org.springframework.data.annotation.Id.class))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No @Id field found in class " + document.getClass().getSimpleName()));

            idField.setAccessible(true);
            Object idValue = idField.get(document);

            return idValue.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get document ID", e);
        }
    }

    /**
     * 원본 도큐먼트를 검색합니다.
     *
     * @param documentClass 도큐먼트 클래스 타입
     * @param documentIds   검색된 도큐먼트 ID 집합
     * @param <T>           도큐먼트 타입
     * @param <ID>          도큐먼트의 ID 타입
     * @return 검색된 원본 도큐먼트 리스트
     */
    private <T, ID> List<T> fetchOriginalDocuments(Class<T> documentClass, Set<String> documentIds) {
        MongoRepository<T, ID> repository = getRepositoryForClass(documentClass);
        Class<?> idType = inferIdClass(repository);

        Set<ID> ids = documentIds.stream()
                .map(idStr -> (ID) convertToIdType(idStr, idType))
                .collect(Collectors.toSet());

        return repository.findAllById(ids);
    }

    /**
     * 리포지토리에서 관리하는 ID 타입을 추론합니다.
     *
     * @param repository 대상 리포지토리
     * @return ID 타입 클래스
     */
    private Class<?> inferIdClass(MongoRepository<?, ?> repository) {
        Class<?>[] generics = GenericTypeResolver.resolveTypeArguments(repository.getClass(), MongoRepository.class);
        if (generics != null && generics.length >= 2) {
            return generics[1];
        }
        return null;
    }

    /**
     * 문자열 ID를 해당 ID 타입으로 변환합니다.
     *
     * @param idStr  문자열로 표현된 ID
     * @param idType ID 타입 클래스
     * @return 변환된 ID 객체
     */
    private Object convertToIdType(String idStr, Class<?> idType) {
        if (idType.equals(UUID.class)) {
            return UUID.fromString(idStr);
        } else if (idType.equals(String.class)) {
            return idStr;
        } else {
            throw new IllegalStateException("Unsupported ID type: " + idType.getName());
        }
    }

    /**
     * 도큐먼트 클래스에 해당하는 MongoRepository를 동적으로 검색합니다.
     *
     * @param documentClass 도큐먼트 클래스 타입
     * @param <T>           도큐먼트 타입
     * @param <ID>          도큐먼트의 ID 타입
     * @return MongoRepository 인스턴스
     */
    @SuppressWarnings("unchecked")
    private <T, ID> MongoRepository<T, ID> getRepositoryForClass(Class<T> documentClass) {
        Map<String, MongoRepository> repositories = applicationContext.getBeansOfType(MongoRepository.class);

        for (MongoRepository<?, ?> repository : repositories.values()) {
            Class<?> repositoryDomainType = inferDomainClass(repository);
            if (repositoryDomainType != null && repositoryDomainType.equals(documentClass)) {
                return (MongoRepository<T, ID>) repository;
            }
        }
        throw new IllegalStateException("No MongoRepository found for class: " + documentClass.getSimpleName());
    }

    /**
     * 리포지토리에서 관리하는 도큐먼트 클래스를 추론합니다.
     *
     * @param repository 대상 리포지토리
     * @return 도큐먼트 클래스 타입
     */
    private Class<?> inferDomainClass(MongoRepository<?, ?> repository) {
        Class<?>[] generics = GenericTypeResolver.resolveTypeArguments(repository.getClass(), MongoRepository.class);
        if (generics != null && generics.length >= 1) {
            return generics[0];
        }
        return null;
    }
}
