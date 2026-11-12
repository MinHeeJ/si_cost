package com.sicost.expenses;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.expenses.ProjectExpenseDtos.*;

@Mapper
public interface ProjectExpenseMapper {
    ProjectState project(@Param("id") String id);
    List<Stored> list(@Param("id") String id);
    Stored find(ExpenseKey key);
    boolean activeItem(@Param("code") String code);
    int greatestSerial(@Param("id") String id,@Param("month") String month);
    void lockClose();
    int insert(ExpenseWrite write);
    int delete(ExpenseKey key);
}
