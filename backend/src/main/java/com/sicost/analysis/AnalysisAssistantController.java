package com.sicost.analysis;

import com.sicost.common.api.*;
import com.sicost.common.excel.ExcelExportService;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static com.sicost.analysis.AnalysisAssistantDtos.*;

@RestController
@RequestMapping("/api/analysis/questions")
public class AnalysisAssistantController {
    private final AnalysisAssistantService service;private final ExcelExportService excel;
    private com.sicost.authentication.AuthenticationTaskService tasks;
    @org.springframework.beans.factory.annotation.Autowired
    public void setAuthenticationTasks(com.sicost.authentication.AuthenticationTaskService tasks) {this.tasks=tasks;}
    private final ExecutorService executor=new ThreadPoolExecutor(2,4,60,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),r->{var t=new Thread(r,"analysis-question");t.setDaemon(true);return t;});
    public AnalysisAssistantController(AnalysisAssistantService service,ExcelExportService excel){this.service=service;this.excel=excel;}
    @jakarta.annotation.PreDestroy public void close(){executor.shutdown();}
    @PostMapping(produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter create(@RequestBody com.fasterxml.jackson.databind.JsonNode input,HttpServletRequest request) {
        requireQuery(request,Set.of());
        var proof=tasks.capture(request);
        var accepted=tasks.run(proof,()->service.accept(AnalysisQuestionInput.parse(input)));var emitter=new SseEmitter(180_000L);
        try{executor.execute(()->{
            try{tasks.run(proof,()->{var result=service.execute(accepted,p->send(emitter,"progress",p));tasks.checkpoint(proof);send(emitter,"final",service.projectFinal(result));emitter.complete();return null;});}
            catch(Exception e){emitter.completeWithError(new IllegalStateException("질문 처리 연결이 종료되었습니다. 기록을 확인해 주세요."));}
        });}catch(RejectedExecutionException e){throw new ApiException(503,"ANALYSIS_BUSY","질문 처리가 많습니다. 잠시 후 다시 시도해 주세요.");}
        return emitter;
    }
    // Disconnection does not cancel accepted work, and never replays a question.
    private static void requireQuery(HttpServletRequest request,Set<String> allowed){request.getParameterMap().forEach((key,values)->{if(!allowed.contains(key)||values.length!=1)throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 조회 항목입니다.",key,null);});}
    private static void send(SseEmitter emitter,String name,Object data){try{emitter.send(SseEmitter.event().name(name).data(ApiResponse.ok(data)));}catch(IOException|IllegalStateException ignored){}}
    @GetMapping public ApiResponse<Page> list(@RequestParam(required=false) String cursor,@RequestParam(defaultValue="50") int limit,HttpServletRequest request){requireQuery(request,Set.of("cursor","limit"));return ApiResponse.ok(service.page(cursor,limit));}
    @GetMapping("/{qnaId}") public ApiResponse<Detail> detail(@PathVariable String qnaId,HttpServletRequest request){requireQuery(request,Set.of());return ApiResponse.ok(service.detail(qnaId));}
    @GetMapping("/{qnaId}/excel") public void excel(@PathVariable String qnaId,HttpServletRequest request,HttpServletResponse response)throws IOException {
        requireQuery(request,Set.of());
        var spec=new ExcelExportService.Specification("AI분석",null,List.of(new ExcelExportService.Column("value","결과",ExcelExportService.CellType.TEXT)),"질문 "+qnaId,"원","UI-26");
        try(var file=excel.generateWorkbook(spec,()->{
            var detail=service.detail(qnaId);if(!detail.status().equals("OK"))throw new ApiException(409,"ANALYSIS_NOT_OK","답변이 완료된 OK 결과만 다운로드할 수 있습니다.");
            List<ExcelExportService.Sheet> sheets=new ArrayList<>();int index=0;
            for(var table:detail.result().tables()){
                var columns=table.columns().stream().map(c->new ExcelExportService.Column(c.key(),c.label()+(c.unit().isEmpty()?"":" ("+c.unit()+")"),c.valueType().equals("DECIMAL")?ExcelExportService.CellType.NUMBER:ExcelExportService.CellType.TEXT)).toList();
                sheets.add(new ExcelExportService.Sheet("결과"+(++index),columns,sink->{
                    for(var row:table.rows())sink.accept(table.columns().stream().map(c->(Object)row.cells().stream().filter(v->v.columnKey().equals(c.key())).findFirst().map(Cell::value).orElse(null)).toList());
                    if(!table.totals().isEmpty())sink.accept(table.columns().stream().map(c->(Object)(c.key().equals(table.columns().get(0).key())?"전체 합계":table.totals().stream().filter(v->v.columnKey().equals(c.key())).findFirst().map(Cell::value).orElse(null))).toList());
                }));
            }
            List<ExcelExportService.Information> info=new ArrayList<>();info.add(new ExcelExportService.Information("질문",detail.question()));info.add(new ExcelExportService.Information("결론",detail.result().conclusion()));info.add(new ExcelExportService.Information("기준시점",detail.result().dataAsOf().toString()));info.add(new ExcelExportService.Information("당시 문맥",detail.result().appliedScope().toString()));
            int tableIndex=0;for(var table:detail.result().tables()){
                String scenario=Map.of("LOW","낮음","BASE","기본","HIGH","높음").get(table.scenario());
                info.add(new ExcelExportService.Information("결과"+(++tableIndex),table.title()+" · "+scenario+" · "+table.periodLabel()+" · "+table.sourceState()+" · "+table.totalRows()+"행"));
                for(var row:table.rows())for(var cell:row.cells())if(cell.reason()!=null)info.add(new ExcelExportService.Information("미확인 "+table.tableId()+" / "+row.rowKey()+" / "+cell.columnKey(),cell.reason()));
            }
            detail.result().assumptions().forEach(v->info.add(new ExcelExportService.Information("가정",v)));detail.result().unknownReasons().forEach(v->info.add(new ExcelExportService.Information("미확인",v)));detail.result().usedData().forEach(v->info.add(new ExcelExportService.Information("사용한 데이터",v.toString())));
            return new ExcelExportService.WorkbookData(sheets,info,detail.result().tables().stream().mapToLong(Table::totalRows).sum(),detail.baseMonth().replace("-",""));
        },request)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");response.setHeader("Content-Disposition",ContentDisposition.attachment().filename(file.filename(),java.nio.charset.StandardCharsets.UTF_8).build().toString());response.setContentLengthLong(file.size());file.transferTo(response.getOutputStream());
        }
    }
}
