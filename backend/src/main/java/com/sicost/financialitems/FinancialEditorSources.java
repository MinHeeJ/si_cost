package com.sicost.financialitems;

import com.sicost.apiops.FinancialReadInput;
import com.sicost.apiops.FinancialReadService;
import com.sicost.corporatetaxesops.*;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class FinancialEditorSources {
    private final CorporateTaxService taxes;
    private final CorporateTaxRepository repository;
    private final CorporateTaxViews views;
    private final FinancialReadService contributions;
    private final com.sicost.b2.FinancialImportRepository imports;
    public FinancialEditorSources(CorporateTaxService taxes,CorporateTaxRepository repository,CorporateTaxViews views,FinancialReadService contributions,com.sicost.b2.FinancialImportRepository imports){this.taxes=taxes;this.repository=repository;this.views=views;this.contributions=contributions;this.imports=imports;}
    public String uploadRevision(YearMonth month){return imports.revision(month.toString().replace("-",""));}
    public Object allocation(String year){return taxes.getCorporateTaxAllocation(year);}
    public CorporateTaxDtos.Allocation allocationValue(String year){return taxes.getCorporateTaxAllocation(year);}
    public Object annual(String year){var value=repository.annual(year);return value==null?null:views.annual(value);}
    public Object contributions(YearMonth month){return contributions.getFinancialContributions(FinancialReadInput.period(month.toString()));}
    public List<CorporateTaxDtos.Payment> payments(YearMonth month,Collection<String> years){
        int year=month.getYear();List<CorporateTaxDtos.Payment> result=new ArrayList<>();
        for(String y:years)for(var p:repository.payments(y)){int value=Integer.parseInt(y);if(value==year||value==year-1&&p.payType().equals("CNFM")||value<year-1&&p.payYn().equals("N"))result.add(views.payment(p));}
        result.sort(Comparator.comparing(CorporateTaxDtos.Payment::dueDate).thenComparing(CorporateTaxDtos.Payment::year).thenComparing(CorporateTaxDtos.Payment::payType));return result;
    }
}
