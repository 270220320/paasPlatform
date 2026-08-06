package com.uavplatform.iam.controller;

import com.uavplatform.common.result.ApiResult;
import com.uavplatform.common.result.IdVO;
import com.uavplatform.common.result.PageResult;
import com.uavplatform.iam.dto.MemberCreateRequest;
import com.uavplatform.iam.dto.MemberUpdateRequest;
import com.uavplatform.iam.dto.MemberVO;
import com.uavplatform.iam.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 人员管理接口（U-002 / U-004）。
 */
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping
    public ApiResult<PageResult<MemberVO>> page(
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) String joinMethod,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "50") long pageSize) {
        return ApiResult.ok(memberService.page(roleId, joinMethod, keyword, page, pageSize));
    }

    @PostMapping
    public ApiResult<IdVO> create(@Valid @RequestBody MemberCreateRequest request) {
        return ApiResult.ok(memberService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @Valid @RequestBody MemberUpdateRequest request) {
        memberService.update(id, request);
        return ApiResult.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        memberService.delete(id);
        return ApiResult.ok(null);
    }
}
