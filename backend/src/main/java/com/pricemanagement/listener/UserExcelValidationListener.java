package com.pricemanagement.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.pricemanagement.dto.UserExcelData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UserExcelValidationListener extends AnalysisEventListener<UserExcelData> {

    private final Map<Integer, String> headers = new LinkedHashMap<>();
    private final List<RowData> rows = new ArrayList<>();

    @Override
    public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
        headers.clear();
        headers.putAll(headMap);
    }

    @Override
    public void invoke(UserExcelData data, AnalysisContext context) {
        rows.add(new RowData(context.readRowHolder().getRowIndex() + 1, data));
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // Validation and persistence happen after the complete sheet has been read.
    }

    public Map<Integer, String> getHeaders() {
        return Map.copyOf(headers);
    }

    public List<RowData> getRows() {
        return List.copyOf(rows);
    }

    public record RowData(int rowNumber, UserExcelData data) {
    }
}
