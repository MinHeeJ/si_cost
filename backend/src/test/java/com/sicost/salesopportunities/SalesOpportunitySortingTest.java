package com.sicost.salesopportunities;

import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.salesopportunitiesops.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static com.sicost.salesopportunities.SalesOpportunityDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SalesOpportunitySortingTest {
    private Stored row(int i) {
        return new Stored("O-"+String.format("%04d",i),"기회","C-001","PART-001","FIND",new BigDecimal("50"),"N",
            new BigDecimal(i%3==0?"2":i%3==1?"10":"100"),"2026-12",null,null,null,null,null,null,
            "WIN",null,"NONE",null,null,i%4==0?null:LocalDate.of(2026,11,1+i%3),null,null,
            "admin",LocalDateTime.of(2026,11,1,0,0),null,null);
    }
    private SalesOpportunityService service() {
        var mapper=mock(SalesOpportunityMapper.class);var repository=mock(SalesOpportunityRepository.class);
        when(repository.mapper()).thenReturn(mapper);
        var rows=new ArrayList<Stored>();for(int i=55;i>=1;i--)rows.add(row(i));when(mapper.all()).thenReturn(rows);
        var commands=mock(SalesCommandRepository.class);var quotes=mock(SalesQuoteCalculation.class);
        return new SalesOpportunityService(repository,commands,quotes,mock(WriteCoordinator.class),mock(RevisionService.class),
            mock(SalesOpportunityTentatives.class),mock(CurrentUserProvider.class),mock(ChangeHistoryWriter.class),
            Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"),ZoneOffset.UTC));
    }
    private List<String> all(SalesOpportunityService service,String sort,String direction) {
        var result=new ArrayList<String>();
        for(int page=1;page<=3;page++) {
            var value=service.list(new Query("WIN",false,null,page,20,sort,direction));
            assertThat(value.total()).isEqualTo(55);
            value.items().forEach(r->result.add(r.id()));
        }
        return result;
    }
    @ParameterizedTest @CsvSource({"amount,ASC","amount,DESC","resultDate,ASC","resultDate,DESC"})
    void headersSortTheWholePopulationNumericallyWithNullsLastAndAscendingPkTies(String sort,String direction) {
        var expected=new ArrayList<String>();
        int[] groups=direction.equals("ASC")?new int[]{0,1,2}:new int[]{2,1,0};
        for(int group:groups)for(int i=1;i<=55;i++)if(i%3==group&&(!sort.equals("resultDate")||i%4!=0))expected.add(row(i).id());
        if(sort.equals("resultDate"))for(int i=1;i<=55;i++)if(i%4==0)expected.add(row(i).id());
        assertThat(all(service(),sort,direction)).containsExactlyElementsOf(expected);
    }
    @Test void omittedSortPreservesResultDateDescendingThenPk() {
        assertThat(all(service(),null,null)).containsExactlyElementsOf(all(service(),"resultDate","DESC"));
    }
}
