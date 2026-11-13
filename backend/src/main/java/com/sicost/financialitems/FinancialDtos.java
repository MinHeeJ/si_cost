package com.sicost.financialitems;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.YearMonth;
import java.util.*;

public final class FinancialDtos {
    private FinancialDtos() {}
    public enum Kind {
        ITEMS("TB_FI_ITEM","itemSn","item_sn","fin_ym","item_sn", "finClass:fin_cls_cd,sgaItemCode:sga_item_cd,itemName:item_nm,amount:amt,vatAmount:vat_amt,ioDate:io_dt,repeat:rpt_yn,remark:rmrk,autoFill:auto_fill_yn,uploadId:upld_id"),
        ADJUSTMENTS("TB_CO_PREV_ADJ","adjustmentId","adj_id","aply_ym","adj_id", "targetMonth:trgt_ym,itemCode:adj_item_cd,amount:adj_amt,reason:adj_rsn"),
        TRANSFERS("TB_FI_XFER","transferId","xfer_sn","xfer_dt","xfer_dt,xfer_sn", "date:xfer_dt,classCode:xfer_cls_cd,itemName:item_nm,receiver:rcvr_nm,amount:amt,remark:rmrk"),
        LOANS("TB_FI_LOAN","loanId","loan_sn","loan_dt","loan_dt,loan_sn", "date:loan_dt,direction:io_cd,amount:amt,counterparty:cntprt_nm,remark:rmrk"),
        CARDS("TB_FI_CARD","cardId","card_id",null,"CASE WHEN card_id ~ '^K-[0-9]+$' THEN substring(card_id from 3)::numeric END NULLS LAST,card_id", "cardName:card_nm,last4:card_no_l4,useYn:use_yn"),
        PAYMENTS("TB_FI_CARD_PAY","cardId","card_id","pay_ym","card_id", "amount:pay_amt"),
        CARD_MONTH("TB_FI_CARD_MON","month","pay_ym","pay_ym","pay_ym", "cardSumAmount:card_sum_amt,cashAmount:cash_amt,editYn:edit_yn"),
        ANNUAL("TB_FI_CORP_TAX","year","tax_yr",null,"tax_yr", "estimatedTax:est_tax_amt"),
        TAX_PAYMENTS("TB_FI_TAX_PAY","year","tax_yr",null,"tax_yr,pay_type_cd", "payType:pay_type_cd,dueDate:pay_due_dt,payAmount:pay_amt,paidDate:pay_dt,payYn:pay_yn");
        public final String table,id,idColumn,scope,order;
        public final Map<String,String> fields;
        Kind(String table,String id,String idColumn,String scope,String order,String fields){
            this.table=table;this.id=id;this.idColumn=idColumn;this.scope=scope;this.order=order;
            Map<String,String> map=new LinkedHashMap<>();for(String entry:fields.split(",")){String[] pair=entry.split(":");map.put(pair[0],pair[1]);}this.fields=Collections.unmodifiableMap(map);
        }
        public String getTable(){return table;} public String getIdColumn(){return idColumn;} public String getScope(){return scope;} public String getOrder(){return order;}
        public boolean isDated(){return this==TRANSFERS||this==LOANS;}
        public boolean isNumericId(){return this==ITEMS||this==ADJUSTMENTS||isDated();}
        public String collection(){return name().toLowerCase(Locale.ROOT);}
    }
    public record Scope(Kind kind,String month,java.time.LocalDate start,java.time.LocalDate end) {
        public static Scope of(Kind kind,YearMonth month){return new Scope(kind,month.toString().replace("-",""),month.atDay(1),month.plusMonths(1).atDay(1));}
    }
    public record Cell(String column,Object value) {}
    public record Mutation(Scope scope,Object id,List<Cell> cells,String actor,java.time.LocalDateTime now) {}
    public record Save(YearMonth month,String expectedRevision,ObjectNode body) {}
    public record Snapshot(Map<Kind,List<ObjectNode>> rows) { public List<ObjectNode> rows(Kind kind){return rows.get(kind);} }
    public record Commit(boolean changed,String revision,String message,Map<String,Object> editor,List<Map<String,String>> createdCards) {}
}
