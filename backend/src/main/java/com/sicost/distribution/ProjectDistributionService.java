package com.sicost.distribution;

import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.validation.ProjectInputPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.distribution.ProjectDistributionDtos.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class ProjectDistributionService {
    private final ProjectDistributionRepository repository;
    private final RevisionService revisions;
    private final CurrentUserProvider users;
    private final Clock clock;
    public ProjectDistributionService(ProjectDistributionRepository repository, RevisionService revisions,
                                      CurrentUserProvider users, Clock clock) {
        this.repository=repository;this.revisions=revisions;this.users=users;this.clock=clock;
    }
    public Data get(String id, String step) {
        Project project=required(id);requireStep(step);
        List<StoredCell> stored=repository.cells(id);
        List<StoredCell> viewed=stored.stream().filter(c->c.step().equals(step)).toList();
        List<String> months=new ArrayList<>();
        YearMonth end=YearMonth.parse(project.endDate().substring(0,7));
        for(YearMonth month=YearMonth.parse(project.startDate().substring(0,7));!month.isAfter(end);month=month.plusMonths(1)) months.add(month.toString());
        Map<String,LinkedHashSet<String>> jobs=new HashMap<>();
        repository.jobs(id).forEach(job->jobs.computeIfAbsent(job.developerId(),key->new LinkedHashSet<>()).add(job.name()));
        Map<Integer,List<StoredCell>> grouped=new TreeMap<>();
        viewed.forEach(c->grouped.computeIfAbsent(c.rowSn(),key->new ArrayList<>()).add(c));
        List<Row> rows=new ArrayList<>();
        for(List<StoredCell> values:grouped.values()) {
            StoredCell first=values.get(0);
            List<Cell> cells=values.stream().sorted(Comparator.comparing(StoredCell::month)).map(c->new Cell(c.month(),money(c.amount()))).toList();
            rows.add(new Row(first.rowSn(),first.rowType(),first.developerId(),first.managementNo(),first.name(),first.itemName(),
                "DEV".equals(first.rowType())?new ArrayList<>(jobs.getOrDefault(first.developerId(),new LinkedHashSet<>())):Collections.emptyList(),
                first.useYn(),cells,money(total(values))));
        }
        List<Cell> totals=months.stream().map(month->new Cell(month,money(viewed.stream().filter(c->month.equals(c.month())).map(StoredCell::amount).reduce(BigDecimal.ZERO,BigDecimal::add)))).toList();
        return new Data(project,step,selectedStep(stored),months,rows,totals,money(total(viewed)),revision(id));
    }
    public Comparison comparison(String id) {
        Project project=required(id);
        return compare(new BigDecimal(project.contractAmount()),repository.cells(id),repository.actuals(id));
    }
    public Badge badge(String id) {
        Comparison result=comparison(id);
        return new Badge(result.selectedStep(),result.selectedStep()!=null,result.totalAmount(),result.differenceAmount(),result.mismatchCount());
    }
    public Comparison prospectiveComparison(String id, String step, List<StoredCell> replacement) {
        Project project=required(id);requireStep(step);
        if(replacement.stream().anyMatch(cell->!step.equals(cell.step()))) throw ProjectInputPolicy.invalid("step");
        List<StoredCell> candidate=new ArrayList<>(repository.cells(id).stream().filter(cell->!step.equals(cell.step())).toList());
        candidate.addAll(replacement);
        return compare(new BigDecimal(project.contractAmount()),candidate,repository.actuals(id));
    }
    public Uploads uploads(String id) { required(id);return new Uploads(repository.uploads(id)); }
    public String revision(String id) { return revisions.revision(repository.aggregate(id)); }
    private Project required(String id) {
        ProjectInputPolicy.text(id,20,"id");users.currentUser();
        Project project=repository.project(id,ProjectInputPolicy.today(clock));
        if(project==null) throw new ApiException(404,"NOT_FOUND","프로젝트를 찾을 수 없습니다.");
        if(!"TK".equals(project.contractType())) throw ProjectInputPolicy.invalid("id");
        return project;
    }
    public static void requireStep(String step) {
        if(step==null || !Set.of("EST","CTRT","PLAN").contains(step)) throw ProjectInputPolicy.invalid("step");
    }
    public static String selectedStep(List<StoredCell> cells) {
        for(String step:List.of("PLAN","CTRT","EST")) if(cells.stream().anyMatch(c->step.equals(c.step()))) return step;
        return null;
    }
    public static Comparison compare(BigDecimal contract, List<StoredCell> stored, List<Actual> actuals) {
        String step=selectedStep(stored);
        List<StoredCell> selected=stored.stream().filter(c->Objects.equals(step,c.step())).toList();
        BigDecimal total=total(selected),difference=contract.subtract(total);
        record PersonMonth(String developerId,String month) {}
        Map<PersonMonth,BigDecimal> amounts=new HashMap<>(),mm=new HashMap<>();
        Map<String,StoredCell> people=new HashMap<>();Map<String,Actual> assigned=new HashMap<>();
        for(StoredCell cell:selected) if("DEV".equals(cell.rowType())) {
            people.put(cell.developerId(),cell);
            amounts.merge(new PersonMonth(cell.developerId(),cell.month()),cell.amount(),BigDecimal::add);
        }
        for(Actual actual:actuals) {
            assigned.put(actual.developerId(),actual);
            mm.merge(new PersonMonth(actual.developerId(),actual.month()),actual.mm(),BigDecimal::add);
        }
        Set<PersonMonth> keys=new HashSet<>(amounts.keySet());keys.addAll(mm.keySet());
        List<Mismatch> items=new ArrayList<>();
        keys.stream().sorted(Comparator.comparing(PersonMonth::developerId).thenComparing(PersonMonth::month)).forEach(key->{
            BigDecimal amount=amounts.getOrDefault(key,BigDecimal.ZERO),actual=mm.getOrDefault(key,BigDecimal.ZERO);
            String type=amount.signum()>0 && actual.signum()==0?"NO_ASSIGNMENT":actual.signum()>0 && !amounts.containsKey(key)?"NO_DISTRIBUTION":null;
            if(type!=null) {
                StoredCell person=people.get(key.developerId());Actual assignment=assigned.get(key.developerId());
                items.add(new Mismatch(key.developerId(),person!=null?person.managementNo():assignment.managementNo(),person!=null?person.name():assignment.name(),
                    key.month(),type,money(amount),actual.setScale(2,RoundingMode.HALF_UP).toPlainString()));
            }
        });
        List<String> warnings=new ArrayList<>();
        if(difference.signum()!=0) warnings.add("계약금액과 배분표 총액이 다릅니다. 차이 "+money(difference)+"원 (경고)");
        if(!items.isEmpty()) warnings.add("배정·배분표 불일치 "+items.size()+"건");
        return new Comparison(step,money(contract),money(total),money(difference),items.size(),items,warnings);
    }
    private static BigDecimal total(List<StoredCell> cells) { return cells.stream().map(StoredCell::amount).reduce(BigDecimal.ZERO,BigDecimal::add); }
    private static String money(BigDecimal value) { return value.setScale(0,RoundingMode.UNNECESSARY).toPlainString(); }
}
