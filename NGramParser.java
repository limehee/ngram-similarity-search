package com.example.demo.ngram;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Set;

/**
 * N-Gram 생성을 담당하는 클래스입니다.
 */
@Component
public class NGramParser {

    /**
     * 주어진 텍스트에 대해 n-gram을 생성합니다.
     *
     * @param text 대상 텍스트
     * @param n    n-gram의 크기 (n값)
     * @return 생성된 n-gram의 리스트
     */
    public Set<String> generateNGrams(String text, int n) {
        Set<String> nGrams = new HashSet<>(); // 중복을 제거하기 위해 Set 사용

        // 텍스트가 null이거나 n보다 작을 경우 빈 리스트 반환
        if (text == null || text.length() < n)
            return nGrams;

        text = preProcessText(text);

        for (int i = 0; i <= text.length() - n; i++) {
            nGrams.add(text.substring(i, i + n));
        }

        return nGrams;
    }

    /**
     * 텍스트 전처리를 수행합니다.
     *
     * @param text 원본 텍스트
     * @return 전처리된 텍스트
     */
    private String preProcessText(String text) {
        text = Normalizer.normalize(text, Normalizer.Form.NFC); // 유니코드 정규화
        text = text.replaceAll("[^가-힣a-zA-Z0-9]", ""); // 한글, 영문, 숫자만 남김
        text = text.toLowerCase(); // 영문 소문자로 변환
        return text.trim(); // 양쪽 공백 제거
    }
}
