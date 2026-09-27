package com.chris64233.labcalibration.web;

import com.chris64233.labcalibration.domain.ReviewRecord;
import com.chris64233.labcalibration.domain.TestResult;
import com.chris64233.labcalibration.service.IssueResultCommand;
import com.chris64233.labcalibration.service.ResultService;
import com.chris64233.labcalibration.service.ReviewService;
import com.chris64233.labcalibration.web.dto.Requests.IssueResultRequest;
import com.chris64233.labcalibration.web.dto.Requests.ReviewRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 检测结果签发、发布与复核接口。
 */
@RestController
@RequestMapping("/api/results")
public class ResultController {

    private final ResultService resultService;
    private final ReviewService reviewService;

    public ResultController(ResultService resultService, ReviewService reviewService) {
        this.resultService = resultService;
        this.reviewService = reviewService;
    }

    /** 签发结果（按结果编号幂等）。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TestResult issue(@Valid @RequestBody IssueResultRequest request) {
        return resultService.issue(new IssueResultCommand(request.resultNo(), request.instrumentCode(),
                request.sampleId(), request.testItem(), request.executedAt(), request.measuredValue()));
    }

    @GetMapping("/{resultNo}")
    public TestResult get(@PathVariable String resultNo) {
        return resultService.getByResultNo(resultNo);
    }

    /** 对外发布；待复核/已替代结果拒绝发布。 */
    @PostMapping("/{resultNo}/publish")
    public TestResult publish(@PathVariable String resultNo) {
        return resultService.publish(resultNo);
    }

    /** 复核决定：确认仍有效或重测替代。需请求头 X-Role: REVIEWER。 */
    @PostMapping("/{resultNo}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewRecord review(@PathVariable String resultNo,
                               @RequestHeader(name = "X-Role", required = false) String role,
                               @Valid @RequestBody ReviewRequest request) {
        ReviewService.RetestCommand retest = request.retest() == null ? null
                : new ReviewService.RetestCommand(request.retest().resultNo(),
                        request.retest().executedAt(), request.retest().measuredValue());
        return reviewService.review(resultNo, request.decision(), request.basis(),
                request.decidedBy(), role, retest);
    }

    /** 复核链查询。 */
    @GetMapping("/{resultNo}/reviews")
    public List<ReviewRecord> reviewChain(@PathVariable String resultNo) {
        return reviewService.reviewChain(resultNo);
    }
}
