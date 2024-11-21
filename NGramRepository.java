package com.example.demo.ngram;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface NGramRepository extends MongoRepository<NGramDocument, String> {

    List<NGramDocument> findByCollectionNameAndFieldAndNgramsInAndN(
            String collectionName, String field, List<String> nGrams, int n);

    List<NGramDocument> findByDocumentIdAndField(String documentId, String field);

    void deleteByCollectionName(String collectionName);
}
