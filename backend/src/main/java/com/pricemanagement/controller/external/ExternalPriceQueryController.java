package com.pricemanagement.controller.external;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.PriceQueryRowDTO;
import com.pricemanagement.dto.Result;
import com.pricemanagement.service.PriceQueryService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/external/v1/price-query")
@RequiredArgsConstructor
public class ExternalPriceQueryController {

    private final PriceQueryService priceQueryService;

    @GetMapping
    @PreAuthorize("hasAuthority('API_price-query:read')")
    public Result<Page<PriceQueryRowDTO>> query(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) CommonStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortDirection) {
        return Result.success("获取价格查询列表成功",
                priceQueryService.query(date, keyword, categoryId, status, page, size, sortBy, sortDirection));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('API_price-query:export')")
    public void export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) CommonStatus status,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortDirection,
            HttpServletResponse response) throws IOException {
        priceQueryService.export(date, keyword, categoryId, status, sortBy, sortDirection, response);
    }
}
