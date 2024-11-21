package com.example.demo.ngram;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Getter
@Setter
@Document(collection = "ngrams")
@CompoundIndexes({
        @CompoundIndex(name = "collection_field_n_ngrams", def = "{'collectionName': 1, 'field': 1, 'n': 1, 'ngrams': 1}"),
        @CompoundIndex(name = "document_field", def = "{'documentId': 1, 'field': 1}")
})
public class NGramDocument {

    @Id
    private String id;
    private String documentId; // 고유 도큐먼트 ID
    private String collectionName; // 원본 도큐먼트 컬렉션 이름
    private String field; // n-gram이 생성된 필드 이름
    private int n; // n-gram 크기
    private List<String> ngrams; // n-gram 데이터 배열
}
