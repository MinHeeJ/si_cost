package com.sicost.uploadsops;

/** Form-specific immutable upload receipts without coupling shared queries to feature packages. */
public interface UploadResultProvider {
    boolean supports(long uploadId);
    Object result(long uploadId);
}
