package com.sicost.salesopportunitiesops;

import com.sicost.budget.ProjectBudgetCalculation;
import com.sicost.common.api.ApiException;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.projects.ProjectRegistrationService;
import com.sicost.projects.ProjectRepository;
import com.sicost.projects.ProjectRules;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.salesopportunitiesops.SalesCommandDtos.*;
import static com.sicost.salesopportunitiesops.SalesCommandService.*;

@Service
public class SalesWinService {
    private final SalesCommandService commands;
    private final SalesCommandRepository repository;
    private final ProjectRegistrationService registration;
    private final ProjectRepository projects;
    private final ChangeHistoryWriter audit;
    public SalesWinService(SalesCommandService commands,SalesCommandRepository repository,ProjectRegistrationService registration,
                           ProjectRepository projects,ChangeHistoryWriter audit){this.commands=commands;this.repository=repository;this.registration=registration;this.projects=projects;this.audit=audit;}
    @Transactional
    public WinResult win(String id,Win input){
        String[] projectId=new String[1];ProjectRegistrationService.Project[] project=new ProjectRegistrationService.Project[1];
        Map<String,BigDecimal> plans=new TreeMap<>();
        SaveResult result=commands.mutate(id,input.expectedRevision(),true,c->{
            commands.requireApproved(c);Opportunity o=c.before();
            for(DraftNeed n:c.needs()){
                BigDecimal total=BigDecimal.valueOf(n.count()).multiply(new BigDecimal(n.monthlyMm())).multiply(BigDecimal.valueOf(n.months()));
                plans.merge(n.jobCode(),total,BigDecimal::add);
            }
            for(var entry:plans.entrySet())if(entry.getValue().compareTo(new BigDecimal("9999.99"))>0||entry.getValue().scale()>2)throw SalesCommandInput.invalid("needs");
            var defaults=projects.defaults(c.sources().month().replace("-",""));
            if(!projects.activeType(input.contractType()))throw SalesCommandInput.invalid("contractType");
            var customer=projects.customer(o.customerId());var part=projects.part(o.partId());
            if(customer==null||!customer.active())throw SalesCommandInput.invalid("customerId");if(part==null||!part.active())throw SalesCommandInput.invalid("partId");
            BigDecimal amount=new BigDecimal(input.contractAmount());
            BigDecimal budget=ProjectBudgetCalculation.preview(amount,defaults.marginRate());if(budget==null)throw SalesCommandInput.invalid("budgetAmount");
            projectId[0]=ProjectRegistrationService.allocate(registration.reservedIds(Collections.emptyList()));
            project[0]=new ProjectRegistrationService.Project(o.name(),o.customerId(),input.contractType(),input.projectStatus(),input.startDate(),input.endDate(),o.partId(),o.workplace(),o.amount(),amount,budget,"N",
                ProjectRules.allowance(null,defaults.teamExpense(),"teamExpenseAmount"),ProjectRules.allowance(null,defaults.projectExpense(),"projectExpenseAmount"),o.memo(),id);
            return with(o,new Transition(o.stage(),new BigDecimal("100.00"),"N","WIN",o.profitRate(),o.approvalStatus(),o.approvedProfitRate(),c.now().toLocalDate(),o.lossReasonCode(),o.lossMemo(),projectId[0]));
        },c->{
            registration.register(projectId[0],project[0],c.actor(),c.now());
            var p=project[0];
            Map<String,Object> fields=new LinkedHashMap<>();fields.put("PROJ_NM",p.name());fields.put("CUST_CLNT_ID",p.customerId());fields.put("CTRT_TYPE_CD",p.contractType());fields.put("PROJ_STAT_CD",p.status());fields.put("CTRT_BGN_DT",p.startDate());fields.put("CTRT_END_DT",p.endDate());fields.put("EST_AMT",p.estimateAmount());fields.put("ORGNL_CTRT_AMT",p.contractAmount());fields.put("CTRT_AMT",p.contractAmount());fields.put("BDGT_AMT",p.budgetAmount());fields.put("BDGT_MANUAL_YN",p.budgetManual());fields.put("TEAM_EXPS_AMT",p.teamExpenseAmount());fields.put("PROJ_EXPS_AMT",p.projectExpenseAmount());fields.put("WORK_PLC_ADDR",p.workplace());fields.put("FEATURE_CN",p.feature());fields.put("OPP_ID",id);
            fields.forEach((field,value)->{if(value!=null)audit.appendSales(new ChangeHistoryWriter.Change("TB_PJ_PROJ",projectId[0],"NEW",field,null,value.toString(),id));});
            plans.forEach((job,mm)->{
                repository.plan(new Plan(projectId[0],job,mm,c.actor(),c.now()));
                audit.appendSales(new ChangeHistoryWriter.Change("TB_PJ_MNPW_PLAN",projectId[0]+":"+job,"NEW","PLAN_MM",null,mm.toPlainString(),id));
                audit.appendSales(new ChangeHistoryWriter.Change("TB_PJ_MNPW_PLAN",projectId[0]+":"+job,"NEW","SRC_CD",null,"OPP",id));
            });
            commands.transitionTentatives(c,"CNVT");
        });
        return new WinResult(result.changed(),result.revision(),result.warnings(),id,projectId[0]);
    }
}
