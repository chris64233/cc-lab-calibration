package com.chris64233.labcalibration;

import com.chris64233.labcalibration.domain.CalibrationConclusion;
import com.chris64233.labcalibration.domain.TestResult;
import com.chris64233.labcalibration.repo.TestResultRepository;
import com.chris64233.labcalibration.service.InstrumentService;
import com.chris64233.labcalibration.service.IssueResultCommand;
import com.chris64233.labcalibration.service.ResultService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发签发：同一结果编号并发提交不得产生重复结果。
 */
@SpringBootTest
class ResultIssuanceConcurrencyTests {

    @Autowired
    private InstrumentService instrumentService;

    @Autowired
    private ResultService resultService;

    @Autowired
    private TestResultRepository resultRepository;

    @Test
    void concurrentIssuanceWithSameResultNoCreatesExactlyOneResult() throws Exception {
        instrumentService.createInstrument("BAL-CONC", "并发天平", 12, Set.of("WEIGHT"));
        instrumentService.addCalibration("BAL-CONC",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T00:00:00Z"),
                CalibrationConclusion.PASS, "省计量院");

        int threads = 16;
        IssueResultCommand cmd = new IssueResultCommand("R-CONC-1", "BAL-CONC", "S-CONC",
                "WEIGHT", Instant.parse("2026-06-01T09:00:00Z"), "10.0g");

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<TestResult>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                gate.await();
                return resultService.issue(cmd);
            }));
        }
        gate.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // 全部线程成功且拿到同一条结果
        List<Long> ids = new ArrayList<>();
        for (Future<TestResult> future : futures) {
            ids.add(future.get().getId());
        }
        assertThat(ids).allMatch(id -> id.equals(ids.getFirst()));
        // 数据库中只有一条该编号的结果
        assertThat(resultRepository.findAll().stream()
                .filter(r -> r.getResultNo().equals("R-CONC-1")))
                .hasSize(1);
    }
}
