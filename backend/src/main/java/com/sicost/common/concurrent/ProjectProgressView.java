package com.sicost.common.concurrent;

/** Implemented by the progress query service so child writes return its complete projection. */
public interface ProjectProgressView {
    Object getProjectProgress(String projectId, Integer year);
}
