package com.sicost.parts;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.concurrent.WriteCoordinator;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.persistence.DatabaseMonthCodec;
import com.sicost.common.validation.ProjectInputPolicy;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.parts.ProjectPartDtos.*;

@Service
public class ProjectPartService {
    private final ProjectPartRepository repository;
    private final WriteCoordinator writes;
    private final RevisionService revisions;
    private final CurrentUserProvider users;
    private final ChangeHistoryWriter history;
    private final Clock clock;
    public ProjectPartService(ProjectPartRepository repository,WriteCoordinator writes,RevisionService revisions,
                              CurrentUserProvider users,ChangeHistoryWriter history,Clock clock) {
        this.repository=repository;this.writes=writes;this.revisions=revisions;this.users=users;this.history=history;this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ProjectPartsData getProjectParts(String id) {
        users.currentUser();ProjectPartInput.id(id);
        return view(id,revisions.revision(repository.snapshot(id)));
    }
    @Transactional
    public SaveProjectPartResult saveProjectPart(String id,SaveProjectPartRequest input) {
        ProjectPartInput.id(id);ProjectInputPolicy.expectedRevision(input.expectedRevision());
        String selected=ProjectInputPolicy.text(input.partId(),20,"partId");
        YearMonth month=ProjectInputPolicy.month(input.effectiveMonth(),"effectiveMonth");
        if (input.reason()!=null && input.reason().codePointCount(0,input.reason().length())>500) throw ProjectInputPolicy.invalid("reason");
        String actor=users.userId();
        ProjectPartsData[] before=new ProjectPartsData[1];PartReference[] reference=new PartReference[1];
        var result=writes.write(List.of("PROJECT:"+id,"operating-standards:CASH_BASE_YM"),input.expectedRevision(),
            () -> repository.snapshot(id),
            () -> {
                repository.lockClose();ProjectSource project=repository.required(id);
                ProjectInputPolicy.requireWritableYear(input.year(),repository.cashBase(),clock);
                if ("LOSE".equals(project.status()) || "END".equals(project.status()) || project.endDate().isBefore(ProjectInputPolicy.today(clock)))
                    throw new ApiException(409,"INVALID_PROJECT_STATUS","종료 또는 실주 프로젝트의 파트는 변경할 수 없습니다.");
                if ("IN".equals(project.contractType())) throw ProjectInputPolicy.invalid("partId");
                before[0]=view(id,input.expectedRevision());
                if (before[0].retainedBasePart()==null) throw new ApiException(409,"INVALID_INPUT","최초 파트 이력을 확인해 주세요.","effectiveMonth",null);
                if (month.isBefore(YearMonth.parse(before[0].minimumMonth())) || repository.protectedMonths().contains(db(month)))
                    throw conflict("CLOSED_MONTH","계약 시작월·운영 시작월·최종 마감 다음 달 이후의 미보호 월을 선택해 주세요.");
                if (month.isAfter(YearMonth.parse(before[0].maximumMonth())) || !month.isAfter(YearMonth.parse(before[0].retainedBasePart().effectiveMonth())))
                    throw conflict("INVALID_INPUT","계약 종료월 이내이며 보존 마지막 시작월보다 뒤인 월을 선택해 주세요.");
                reference[0]=repository.reference(selected);
                if (reference[0]==null || !"Y".equals(reference[0].useYn())) throw ProjectInputPolicy.invalid("partId");
            },
            () -> !before[0].replacementTargets().isEmpty() || !Objects.equals(selected,before[0].retainedBasePart().partId()),
            () -> {
                int serial=ProjectInputPolicy.nextSerial(repository.greatestHistorySn(id));
                LocalDateTime now=LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);
                boolean cancel=Objects.equals(selected,before[0].retainedBasePart().partId());
                String oldValue=cancel?reservationSummary(before[0].replacementTargets()):label(before[0].retainedBasePart());
                String newValue=cancel?"변경 취소":reference[0].name()+" ("+month+"부터)";
                for (ProjectPart row:before[0].replacementTargets()) repository.delete(id,db(YearMonth.parse(row.effectiveMonth())));
                if (!cancel) repository.insert(new PartInsert(id,db(month),selected,input.reason(),actor,now));
                repository.history(new PartHistory(id,serial,oldValue,newValue,actor,now));
                history.append(new ChangeHistoryWriter.Change("TB_PJ_PROJ",id,"UPD","영업 파트",oldValue,newValue,input.reason()));
            });
        return new SaveProjectPartResult(view(id,result.revision()),result.revision(),result.changed());
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<PartPeriod> timeline(String id) {
        users.currentUser();repository.required(ProjectPartInput.id(id));List<PartSource> rows=repository.parts(id);
        List<PartPeriod> periods=new ArrayList<>();
        for (int i=0;i<rows.size();i++) {
            PartSource row=rows.get(i);
            String end=i+1==rows.size()?null:api(rows.get(i+1).month()).minusMonths(1).toString();
            periods.add(new PartPeriod(api(row.month()).toString(),end,row.partId(),row.partName()));
        }
        return List.copyOf(periods);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Optional<PartPeriod> partAt(String id,String month) {
        YearMonth target=ProjectInputPolicy.month(month,"month");
        return timeline(id).stream().filter(p->!target.isBefore(YearMonth.parse(p.effectiveMonth()))
            && (p.endMonth()==null || !target.isAfter(YearMonth.parse(p.endMonth())))).findFirst();
    }
    private ProjectPartsData view(String id,String revision) {
        ProjectSource project=repository.required(id);YearMonth base=repository.cashBase();
        YearMonth minimum=YearMonth.from(project.startDate());if (base.isAfter(minimum)) minimum=base;
        String closed=repository.lastClose();
        if (closed!=null && api(closed).plusMonths(1).isAfter(minimum)) minimum=api(closed).plusMonths(1);
        Set<String> protectedMonths=new HashSet<>(repository.protectedMonths());
        List<PartSource> rows=repository.parts(id);List<ProjectPart> items=new ArrayList<>(),targets=new ArrayList<>();
        ProjectPart retained=null;YearMonth current=YearMonth.from(ProjectInputPolicy.today(clock));
        for (int i=0;i<rows.size();i++) {
            PartSource row=rows.get(i);YearMonth start=api(row.month());
            ProjectPart item=new ProjectPart(start.toString(),row.partId(),row.partName(),row.reason(),row.actor(),start.isAfter(current));
            items.add(item);
            if (i==0 || start.isBefore(base) || protectedMonths.contains(row.month())) retained=item;else targets.add(item);
        }
        return new ProjectPartsData(List.copyOf(items),List.copyOf(targets),retained,minimum.toString(),YearMonth.from(project.endDate()).toString(),revision);
    }
    private String reservationSummary(List<ProjectPart> rows) {
        String all=String.join("; ",rows.stream().map(p->label(p)+" ("+p.effectiveMonth()+"부터)").toList());
        if (all.codePointCount(0,all.length())<=500) return all;
        ProjectPart first=rows.get(0),last=rows.get(rows.size()-1);
        return "예약 "+rows.size()+"건 취소: "+first.effectiveMonth()+" "+label(first)+" ~ "+last.effectiveMonth()+" "+label(last)+" (전체 교체 대상)";
    }
    private String label(ProjectPart row) { return row.partName()==null?Objects.toString(row.partId(),"미지정"):row.partName(); }
    private ApiException conflict(String code,String message) { return new ApiException(409,code,message,"effectiveMonth",null); }
    private static YearMonth api(String value) { return YearMonth.parse(DatabaseMonthCodec.toApi(value)); }
    private static String db(YearMonth value) { return DatabaseMonthCodec.toDatabase(value.toString()); }
}
