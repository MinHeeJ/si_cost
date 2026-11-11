package com.sicost.areasops;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public final class AddressDtos {
    private AddressDtos() {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Area(String code, String level, String parentCode, String name, String useYn) {}
    public record Areas(List<Area> items) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AreaPath(List<Area> items, String homeAddress) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Selection(String homeAreaCode, String homeAddress) {}
}
