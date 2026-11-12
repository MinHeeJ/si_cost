package com.sicost.distribution;

import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.distribution.ProjectDistributionDtos.*;

@Repository
public class ProjectDistributionRepository {
    private final ProjectDistributionMapper mapper;
    public ProjectDistributionRepository(ProjectDistributionMapper mapper) { this.mapper=mapper; }
    public Project project(String id, LocalDate today) { return mapper.project(new ProjectLookup(id,today)); }
    public List<StoredCell> cells(String id) { return mapper.cells(id); }
    public List<Job> jobs(String id) { return mapper.jobs(id); }
    public List<Actual> actuals(String id) { return mapper.actuals(id); }
    public List<UploadRecord> uploads(String id) { return mapper.uploads(id); }
    public Aggregate aggregate(String id) { return mapper.aggregate(id); }
}
