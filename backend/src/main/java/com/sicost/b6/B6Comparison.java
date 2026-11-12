package com.sicost.b6;

import com.sicost.common.api.ApiException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import static com.sicost.b6.B6Dtos.*;

public final class B6Comparison {
    private B6Comparison() {}
    public static List<Cell> selected(List<Cell> cells) {
        for(String step:List.of("PLAN","CTRT","EST")) {
            var table=cells.stream().filter(c->c.step().equals(step)).toList();if(!table.isEmpty())return table;
        }
        return cells;
    }
    public static List<Cell> substitute(List<Cell> stored,String step,List<Cell> incoming) {
        var result=new ArrayList<>(stored.stream().filter(c->!c.step().equals(step)).toList());result.addAll(incoming);return result;
    }
    public static Comparison compare(Project project,List<Cell> all,List<Actual> actuals,List<Developer> developers) {
        var selected=selected(all);String step=selected.isEmpty()?null:selected.get(0).step();
        BigDecimal total=selected.stream().map(Cell::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal difference=project.contractAmount().subtract(total);
        Map<PersonMonth,BigDecimal> amounts=new LinkedHashMap<>(),mm=new LinkedHashMap<>();
        Map<String,Developer> names=new HashMap<>();developers.forEach(d->names.put(d.id(),d));
        for(Cell cell:selected)if(cell.rowType().equals("DEV"))amounts.merge(new PersonMonth(cell.developerId(),cell.month()),cell.amount(),BigDecimal::add);
        for(Actual actual:actuals) {
            mm.merge(new PersonMonth(actual.developerId(),actual.month()),actual.mm(),BigDecimal::add);
            names.putIfAbsent(actual.developerId(),new Developer(actual.developerId(),actual.managementNo(),actual.name(),null));
        }
        var keys=new TreeSet<PersonMonth>(Comparator.comparing(PersonMonth::developerId).thenComparing(PersonMonth::month));keys.addAll(amounts.keySet());keys.addAll(mm.keySet());
        List<Mismatch> items=new ArrayList<>();
        for(var key:keys) {
            var amount=amounts.getOrDefault(key,BigDecimal.ZERO);var actual=mm.getOrDefault(key,BigDecimal.ZERO);
            String type=amount.signum()>0&&actual.signum()==0?"NO_ASSIGNMENT":actual.signum()>0&&!amounts.containsKey(key)?"NO_DISTRIBUTION":null;
            if(type!=null) {
                var developer=names.get(key.developerId());
                if(developer==null)throw new IllegalStateException("배분표 인력 원천을 확인하지 못했습니다.");
                items.add(new Mismatch(key.developerId(),developer.managementNo(),developer.name(),displayMonth(key.month()),type,amount.toPlainString(),actual.setScale(2).toPlainString()));
            }
        }
        List<String> warnings=new ArrayList<>();
        if(difference.signum()!=0)warnings.add("계약금액과 계산 선택표의 차이 "+difference.toPlainString()+"원");
        if(!items.isEmpty())warnings.add("계산 선택표 배정/배분 불일치 "+items.size()+"건");
        return new Comparison(step,project.contractAmount().toPlainString(),total.toPlainString(),difference.toPlainString(),items.size(),List.copyOf(items),List.copyOf(warnings));
    }
    public static void protect(List<Cell> before,List<Cell> after,Set<String> protectedMonths,YearMonth cashBase) {
        var old=protectedAmounts(selected(before),protectedMonths,cashBase);
        var next=protectedAmounts(selected(after),protectedMonths,cashBase);
        var keys=new LinkedHashSet<>(old.keySet());keys.addAll(next.keySet());
        List<ProtectedDifference> changes=new ArrayList<>();
        for(var key:keys) {
            var a=old.getOrDefault(key,BigDecimal.ZERO);var b=next.getOrDefault(key,BigDecimal.ZERO);
            if(a.compareTo(b)!=0)changes.add(new ProtectedDifference(displayMonth(key.month()),key.rowType(),key.identity(),a.toPlainString(),b.toPlainString(),b.subtract(a).toPlainString()));
        }
        if(!changes.isEmpty())throw new ApiException(409,"CLOSED_MONTH","마감·이관·고정 월의 계산 선택표 금액은 변경할 수 없습니다.","step",Map.of("differences",changes));
    }
    private static Map<ProtectedKey,BigDecimal> protectedAmounts(List<Cell> cells,Set<String> months,YearMonth cashBase) {
        Map<ProtectedKey,BigDecimal> sums=new LinkedHashMap<>();String base=cashBase.toString().replace("-","");
        for(Cell cell:cells)if(months.contains(cell.month())||cell.month().compareTo(base)<0) {
            var key=new ProtectedKey(cell.month(),cell.rowType(),cell.rowType().equals("DEV")?cell.developerId():cell.itemName());
            sums.merge(key,cell.amount(),BigDecimal::add);
        }
        return sums;
    }
    public static String displayMonth(String month) {return month.substring(0,4)+"-"+month.substring(4);}
    private record PersonMonth(String developerId,String month) {}
    private record ProtectedKey(String month,String rowType,String identity) {}
    public record ProtectedDifference(String month,String rowType,String identity,String before,String after,String difference) {}
}
