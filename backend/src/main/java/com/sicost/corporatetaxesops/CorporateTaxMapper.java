package com.sicost.corporatetaxesops;

import java.util.List;
import org.apache.ibatis.annotations.*;
import static com.sicost.corporatetaxesops.CorporateTaxDtos.*;

@Mapper
public interface CorporateTaxMapper {
    StoredAnnual annual(@Param("year") String year);
    List<StoredPayment> payments(@Param("year") String year);
    List<StoredMonth> months(@Param("year") String year);
    int insertAnnual(AnnualWrite write);
    int insertPayment(PaymentWrite write);
    int confirm(ConfirmationWrite write);
    void lockTimeout();
}
