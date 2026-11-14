package com.sicost.imports;

import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.MasterLockMapper;
import com.sicost.common.concurrent.WriteCoordinator;
import com.sicost.common.validation.ProjectInputPolicy;
import com.sicost.uploadsops.UploadResultProvider;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import static com.sicost.imports.BusinessPlanImportDtos.*;

@Service
public class BusinessPlanImportService implements UploadResultProvider {
    private final BusinessPlanImportMapper mapper;
    private final BusinessPlanImportRepository repository;
    private final BusinessPlanImportDefinition definition;
    private final WriteCoordinator writes;
    private final MasterLockMapper master;
    private final TransactionTemplate transaction;
    public BusinessPlanImportService(BusinessPlanImportMapper mapper, BusinessPlanImportRepository repository,
        BusinessPlanImportDefinition definition, WriteCoordinator writes, MasterLockMapper master, PlatformTransactionManager manager) {
        this.mapper = mapper; this.repository = repository; this.definition = definition; this.writes = writes; this.master = master;
        transaction = new TransactionTemplate(manager); transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Validation validate(MultipartFile file, int year) {
        String filename = definition.filename(file);
        return transaction.execute(status -> {
            mapper.lockTimeout(); master.lockClose(); final Validation[] result = {null};
            writes.create(keys(year, null), () -> mapper.snapshot(year), () -> writable(year), () -> {
                Checked checked = check(file, year);
                long id = repository.create(write(0, filename, file.getSize(), year, checked), checked);
                result[0] = repository.validation(id, year, checked);
            });
            return result[0];
        });
    }
    public Applied apply(MultipartFile file, Request request) {
        String filename = definition.filename(file); long id = id(request.uploadId());
        if (!Set.of("ALL", "KEEP_CLOSED").contains(Objects.toString(request.mode(), ""))) throw ProjectInputPolicy.invalid("mode");
        try {
            Object result = transaction.execute(status -> {
                mapper.lockTimeout(); master.lockClose(); final Upload[] upload = {null}; final Checked[] checked = {null};
                writes.write(keys(request.year(), id), request.expectedRevision(), () -> {
                    if (upload[0] == null) {
                        upload[0] = repository.required(id, true); context(upload[0], filename, file.getSize(), request.year());
                        if ("APPLIED".equals(upload[0].status())) throw new Replay(repository.replay(upload[0], request));
                    }
                    return mapper.snapshot(request.year());
                }, () -> {
                    writable(request.year()); checked[0] = check(file, request.year());
                    repository.replace(write(id, filename, file.getSize(), request.year(), checked[0]), checked[0]);
                }, () -> checked[0].canApply() && checked[0].changes().stream().anyMatch(c -> c.changed() && !("KEEP_CLOSED".equals(request.mode()) && c.closed())),
                    () -> repository.applyTargets(upload[0], checked[0], request.mode(), mapper.targets(request.year())));
                if (!checked[0].canApply()) return repository.validation(id, request.year(), checked[0]);
                BusinessPlanImportRepository.one(mapper.markApplied(write(id, filename, file.getSize(), request.year(), checked[0])));
                Upload applied = repository.required(id, false);
                List<String> changed = new ArrayList<>(), unchanged = new ArrayList<>(), kept = new ArrayList<>();
                for (Change c : checked[0].changes()) {
                    if ("KEEP_CLOSED".equals(request.mode()) && c.closed()) kept.add(c.month());
                    else if (c.changed()) changed.add(c.month()); else unchanged.add(c.month());
                }
                return new Applied(Long.toString(id), "APPLIED", checked[0].summary(), false, "목표 반영이 완료되었습니다.",
                    applied.appliedAt().atZone(com.sicost.common.TimeConfiguration.SEOUL).toOffsetDateTime().toString(),
                    changed, unchanged, kept, checked[0].warnings(), request.year(), request.mode());
            });
            if (result instanceof Validation validation) throw new ApiException(422, "REVALIDATION_FAILED", "최신 검사 오류를 수정하고 다시 검사해 주세요.",
                "file", Map.of("financial", Map.of("upload", validation)));
            return (Applied) result;
        } catch (Replay replay) { return replay.result; }
    }
    private Checked check(MultipartFile file, int year) { return definition.check(definition.parse(file), year, mapper.targets(year), mapper.closes(year)); }
    private void writable(int year) {
        if (year != repository.now().getYear()) throw new ApiException(409, "PERIOD_READ_ONLY", "올해 연간 목표만 업로드할 수 있습니다. 지난 해는 읽기 전용입니다.");
    }
    private Set<String> keys(int year, Long id) {
        Set<String> keys = new TreeSet<>(List.of("BUSINESS_PLAN_YEAR:" + year, "operating-standards:CASH_BASE_YM"));
        if (id != null) keys.add("UPLOAD:" + id); return keys;
    }
    private void context(Upload upload, String filename, long size, int year) {
        if (!"B10".equals(upload.formType()) || !"UI-18".equals(upload.screenId()) || upload.projectId() != null || upload.step() != null
            || !repository.actor().equals(upload.actor()) || !filename.equals(upload.filename()) || size != upload.size()
            || !String.format(Locale.ROOT, "%04d01", year).equals(upload.month()) || !Set.of("CHECKED", "APPLIED").contains(upload.status()))
            throw new ApiException(409, "UPLOAD_CONTEXT_MISMATCH", "같은 처리자·양식·화면·파일명·크기·연도의 검사 번호를 사용해 주세요.");
    }
    private UploadWrite write(long id, String filename, long size, int year, Checked checked) {
        return new UploadWrite(id, filename, size, String.format(Locale.ROOT, "%04d01", year), checked.summary(), repository.actor(), repository.now());
    }
    public static long id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,11}")) throw ProjectInputPolicy.invalid("uploadId"); return Long.parseLong(value);
    }
    public static int year(String value) {
        if (value == null || !value.matches("[0-9]{1,4}") || Integer.parseInt(value) < 1) throw ProjectInputPolicy.invalid("year");
        return Integer.parseInt(value);
    }
    @Override public boolean supports(long id) { Upload upload = mapper.upload(id); return upload != null && "B10".equals(upload.formType()); }
    @Override public Object result(long id) {
        return transaction.execute(status -> {
            Upload upload = repository.required(id, false);
            if (!"B10".equals(upload.formType()) || !"UI-18".equals(upload.screenId()) || !repository.actor().equals(upload.actor()))
                throw new ApiException(409, "UPLOAD_CONTEXT_MISMATCH", "업로드 문맥을 확인해 주세요.");
            return repository.result(upload);
        });
    }
    private static final class Replay extends RuntimeException {
        final Applied result; Replay(Applied result) { this.result = result; }
    }
}
