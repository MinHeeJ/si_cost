package com.sicost.uploadsops;

import java.util.List;

public final class UploadIssuesDtos {
    private UploadIssuesDtos() {}
    public record Issue(int sequence, String severity, int rowNumber, String columnName, String inputValue, String reason) {}
    public record Page(List<Issue> items, long total, int page, int size) {}
    public record Query(long uploadId, String severity, long offset, int limit) {}
}
