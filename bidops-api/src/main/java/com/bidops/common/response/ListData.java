package com.bidops.common.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

/**
 * 목록 응답의 data 필드: { items: [...], total_count: N }
 */
@Getter
public class ListData<T> {

    private final List<T> items;

    @JsonProperty("total_count")
    private final long totalCount;

    public ListData(List<T> items, long totalCount) {
        this.items = items;
        this.totalCount = totalCount;
    }
}
