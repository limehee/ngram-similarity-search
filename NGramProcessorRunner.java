package com.example.demo.ngram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NGramProcessorRunner implements CommandLineRunner {

    private final NGramProcessor nGramProcessor;

    /**
     * 애플리케이션 시작 시 N-Gram 데이터의 유효성을 검증하고 필요 시 재생성합니다.
     *
     * @param args 명령행 인자
     * @throws Exception 예외 발생 시
     */
    @Override
    public void run(String... args) throws Exception {
        try {
            nGramProcessor.validateAllNGrams();
            log.info("NGram validation completed successfully");
        } catch (Exception e) {
            log.error("NGram validation failed", e);
        }
    }
}
