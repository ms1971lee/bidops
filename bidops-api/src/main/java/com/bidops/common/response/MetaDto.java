package com.bidops.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MetaDto {
    private Integer page;
    private Integer size;
    @JsonProperty("total_count")
    private Long totalCount;

    public static MetaDto of(int page, int size, long totalCount) {
        return MetaDto.builder().page(page).size(size).totalCount(totalCount).build();
    }

    public static MetaDto empty() {
        return MetaDto.builder().build();
    }
}
