package com.sicost.financialitems;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.financialitems.FinancialDtos.*;

@Mapper
public interface FinancialMapper {
    List<String> rows(Scope scope);
    List<String> closedMonths();
    String code(@Param("group") String group,@Param("code") String code);
    String nextId(@Param("kind") Kind kind);
    String greatestCardNumber();
    int insert(Mutation mutation);
    int update(Mutation mutation);
    int delete(Mutation mutation);
    void lockTimeout();
}
