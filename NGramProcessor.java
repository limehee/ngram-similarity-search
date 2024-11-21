package com.example.demo.ngram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Slf4j
public class NGramProcessor {

    private final ApplicationContext applicationContext;
    private final NGramRepository nGramRepository;
    private final NGramParser nGramParser;

    // 클래스별 어노테이션 필드 캐싱
    private final Map<Class<?>, List<Field>> annotatedFieldsCache = new HashMap<>();

    /**
     * 모든 도큐먼트 클래스에 대해 NGramField를 검증하고 필요 시 재생성합니다.
     * 각 도큐먼트 클래스의 N-그램 데이터의 일관성을 확인하고, 불일치나 누락된 데이터가 있을 경우 재생성합니다.
     */
    public void validateAllNGrams() {
        Map<String, MongoRepository> repositories = applicationContext.getBeansOfType(MongoRepository.class);

        for (MongoRepository<?, ?> repository : repositories.values()) {
            Class<?> documentClass = inferDocumentClass(repository);
            if (documentClass == null) {
                log.info("Skipping repository: Unable to infer document class for repository {}", repository.getClass().getSimpleName());
                continue;
            }

            // 어노테이션이 붙은 필드를 캐싱
            List<Field> annotatedFields = getAnnotatedFields(documentClass);
            if (annotatedFields.isEmpty()) {
                continue; // 어노테이션 없는 클래스는 건너뜀
            }

            processValidation(repository, documentClass, annotatedFields);
        }
    }

    /**
     * 주어진 리포지토리와 도큐먼트 클래스, 어노테이션이 적용된 필드를 기반으로 NGram 데이터를 검증하고,
     * 필요 시 NGram 데이터를 재생성합니다.
     *
     * @param repository      검증할 도큐먼트를 관리하는 MongoRepository
     * @param documentClass   검증할 도큐먼트 클래스 타입
     * @param annotatedFields NGramField 어노테이션이 적용된 필드 리스트
     */
    private void processValidation(MongoRepository<?, ?> repository, Class<?> documentClass, List<Field> annotatedFields) {
        // 클래스 단위로 validate 수행
        if (validateClass(repository, documentClass, annotatedFields)) {
            regenerateAllForClass(repository, annotatedFields);
            log.info("NGrams regenerated for document class: {}", documentClass.getSimpleName());
        } else {
            log.info("No NGram mismatches or missing data for document class: {}", documentClass.getSimpleName());
        }
    }

    /**
     * 도큐먼트 클래스에서 NGramField 어노테이션이 붙은 필드들을 반환합니다. (캐싱 활용)
     *
     * @param clazz 대상 도큐먼트 클래스
     * @return NGramField 어노테이션이 붙은 필드들의 리스트
     */
    private List<Field> getAnnotatedFields(Class<?> clazz) {
        return annotatedFieldsCache.computeIfAbsent(clazz, key ->
                Stream.of(key.getDeclaredFields())
                        .filter(field -> field.isAnnotationPresent(NGramField.class))
                        .toList()
        );
    }

    /**
     * 특정 도큐먼트 클래스에 대해 n 값의 불일치나 데이터 누락 여부를 확인합니다.
     *
     * @param repository      대상 리포지토리
     * @param documentClass   도큐먼트 클래스
     * @param annotatedFields 검증할 필드들의 리스트
     * @return 불일치나 누락이 발견되면 true를 반환
     */
    private boolean validateClass(MongoRepository<?, ?> repository, Class<?> documentClass, List<Field> annotatedFields) {
        boolean mismatchFound = false;

        List<?> documents = repository.findAll();
        for (Object document : documents) {
            for (Field field : annotatedFields) {
                try {
                    field.setAccessible(true);
                    String fieldValue = (String) field.get(document);

                    if (fieldValue != null) {
                        NGramField annotation = field.getAnnotation(NGramField.class);
                        int n = annotation.value();

                        List<NGramDocument> existingNGrams = nGramRepository.findByDocumentIdAndField(getDocumentId(document), field.getName());
                        if (!existingNGrams.isEmpty() && existingNGrams.getFirst().getN() != n) {
                            if (annotation.failOnMismatch()) {
                                throw new IllegalStateException(String.format(
                                        "NGram mismatch detected for document class '%s', document '%s', field '%s': stored n=%d, expected n=%d",
                                        documentClass.getSimpleName(), getDocumentId(document), field.getName(), existingNGrams.getFirst().getN(), n
                                ));
                            } else {
                                mismatchFound = true;
                            }
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Failed to validate NGramField: " + field.getName(), e);
                }
            }
        }

        // 불일치 또는 누락 발견 여부 반환
        return mismatchFound || documents.stream()
                .anyMatch(document -> annotatedFields.stream().anyMatch(field -> isMissingNGrams(document, field)));
    }

    /**
     * 특정 도큐먼트와 필드에 대해 NGram 데이터의 누락 여부를 확인합니다.
     *
     * @param document 대상 도큐먼트 객체
     * @param field    대상 필드
     * @return NGram 데이터가 누락되었으면 true를 반환
     */
    private boolean isMissingNGrams(Object document, Field field) {
        try {
            return nGramRepository.findByDocumentIdAndField(getDocumentId(document), field.getName()).isEmpty();
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 클래스의 모든 인스턴스에 대해 NGram 데이터를 재생성합니다.
     *
     * @param repository      대상 리포지토리
     * @param annotatedFields NGramField 어노테이션이 붙은 필드들의 리스트
     */
    private void regenerateAllForClass(MongoRepository<?, ?> repository, List<Field> annotatedFields) {
        List<?> documents = repository.findAll();
        List<NGramDocument> newNGrams = new ArrayList<>();

        for (Object document : documents) {
            for (Field field : annotatedFields) {
                try {
                    field.setAccessible(true);
                    String fieldValue = (String) field.get(document);

                    if (fieldValue != null) {
                        NGramField annotation = field.getAnnotation(NGramField.class);
                        int n = annotation.value();

                        Set<String> nGrams = nGramParser.generateNGrams(fieldValue, n);

                        NGramDocument nGramDocument = new NGramDocument();
                        nGramDocument.setId(UUID.randomUUID().toString());
                        nGramDocument.setDocumentId(getDocumentId(document));
                        nGramDocument.setField(field.getName());
                        nGramDocument.setCollectionName(document.getClass().getSimpleName());
                        nGramDocument.setN(n);
                        nGramDocument.setNgrams(new ArrayList<>(nGrams));

                        newNGrams.add(nGramDocument);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Failed to regenerate NGrams for field: " + field.getName(), e);
                }
            }
        }

        // 기존 데이터 삭제 후 새 데이터 저장
        nGramRepository.deleteByCollectionName(repository.getClass().getSimpleName());
        nGramRepository.saveAll(newNGrams);
    }

    /**
     * 도큐먼트 객체에서 ID 값을 추출합니다.
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
            throw new RuntimeException("Failed to extract document ID using @Id annotation", e);
        }
    }

    /**
     * 리포지토리에서 관리하는 도큐먼트 클래스를 추론합니다.
     *
     * @param repository 대상 리포지토리
     * @return 리포지토리가 관리하는 도큐먼트 클래스
     */
    private Class<?> inferDocumentClass(MongoRepository<?, ?> repository) {
        try {
            // 리포지토리의 실제 도큐먼트 클래스를 추론
            Class<?>[] generics = GenericTypeResolver.resolveTypeArguments(repository.getClass(), MongoRepository.class);
            if (generics != null && generics.length > 0) {
                return generics[0];
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to infer document class for repository {}", repository.getClass().getSimpleName(), e);
            return null;
        }
    }
}
