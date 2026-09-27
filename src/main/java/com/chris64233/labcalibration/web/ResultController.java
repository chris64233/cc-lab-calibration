package com.chris64233.labcalibration.web;

import com.chris64233.labcalibration.domain.ReviewDecision;
import com.chris64233.labcalibration.domain.ReviewRecord;
import com.chris64233.labcalibration.service.BusinessException;
import com.chris64233.labcalibration.service.IssueResultCommand;
import com.chris64233.labcalibration.service.ResultService;
import com.chris64233.labcalibration.service.ReviewService;
import com.chris64233.labcalibration.web.dto.Requests.IssueResultRequest;
import com.chris64233.labcalibration.web.dto.Requests.ReviewRequest;
import com.chris64233.labcalibration.web.dto.Responses.ReviewRecordResponse;
import com.chris64233.labcalibration.web.dto.Responses.TestResultResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 检测结果签发、发布与复核。 */
@RestController
@RequestMapping("/api/results")
public class ResultController {

    private final ResultService resultService;
    private final ReviewService reviewService;

    public ResultController(ResultService resultService, ReviewService reviewService) {
        this.resultService = resultService;
        this.reviewService = reviewService;
    }

    /** 签发检测结果（幂等：同一 requestId 重复提交返回同一结果）。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TestResultResponse issue(@Valid @RequestBody IssueResultRequest request) {
        return TestResultResponse.of(resultService.issue(toCommand(request)));
    }

    @GetMapping("/{resultNo}")
    public TestResultResponse get(@PathVariable String resultNo) {
        return TestResultResponse.of(resultService.getByResultNo(resultNo));
    }

    /** 对外发布。待复核 / 已被替代的结果禁止发布。 */
    @PostMapping("/{resultNo}/publish")
    public TestResultResponse publish(@PathVariable String resultNo) {
        return TestResultResponse.of(resultService.publish(resultNo));
    }

    /** 复核处置：CONFIRM_VALID 确认有效；RETEST 重测替代（须携带新结果签发参数）。 */
    @PostMapping("/{resultNo}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewRecordResponse review(@PathVariable String resultNo,
                                       @Valid @RequestBody ReviewRequest request) {
        ReviewRecord record;
        if (request.decision() == ReviewDecision.CONFIRM_VALID) {
            record = reviewService.confirmValid(resultNo, request.decidedBy(),
                    request.reviewerRole(), request.rationale());
        } else if (request.decision() == ReviewDecision.RETEST) {
            if (request.retest() == null) {
                throw BusinessException.badRequest("重测替代必须提供 retest 签发参数");
            }
            record = reviewService.retest(resultNo, request.decidedBy(), request.reviewerRole(),
                    request.rationale(), toCommand(request.retest()));
        } else {
            throw BusinessException.badRequest("不支持的复核决定: " + request.decision());
        }
        return ReviewRecordResponse.of(record);
    }

    /** 复核链查询。 */
    @GetMapping("/{resultNo}/reviews")
    public List<ReviewRecordResponse> reviewChain(@PathVariable String resultNo) {
        return reviewService.reviewChain(resultNo).stream()
                .map(ReviewRecordResponse::of)
                .toList();
    }

    static IssueResultCommand toCommand(IssueResultRequest r) {
        return new IssueResultCommand(r.requestId(), r.instrumentCode(), r.sampleId(),
                r.testItem(), r.executedAt(), r.measurement(), r.unit());
    }
}
