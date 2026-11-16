package com.sicost.resume;

import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiResponse;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import static com.sicost.resume.ResumeExtractionDtos.*;
import com.sicost.resume.ResumeExtractionDtos.Dictionary;

@Service
public class ResumeExtractionService {
    private final ResumeExtractionMapper mapper;private final ResumeDocumentParser parser;private final ResumeFieldExtractor extractor;
    private final CurrentUserProvider users;private final Clock clock;private final TransactionTemplate snapshot;
    private final ExecutorService workers=new ThreadPoolExecutor(2,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->{var t=new Thread(r,"resume-extraction");t.setDaemon(true);return t;});
    @jakarta.annotation.PreDestroy public void close(){workers.shutdownNow();}
    public ResumeExtractionService(ResumeExtractionMapper mapper,ResumeDocumentParser parser,ResumeFieldExtractor extractor,CurrentUserProvider users,Clock clock,PlatformTransactionManager transactions){
        this.mapper=mapper;this.parser=parser;this.extractor=extractor;this.users=users;this.clock=clock;snapshot=new TransactionTemplate(transactions);snapshot.setReadOnly(true);snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);snapshot.setTimeout(5);
    }
    public Extraction extract(byte[] bytes,String filename,String target){
        users.currentUser();if(!Set.of("DEVELOPER","CANDIDATE").contains(target))throw ResumeDocumentParser.error(400,"대상은 DEVELOPER 또는 CANDIDATE여야 합니다.");
        Future<Extraction> work;
        try{work=workers.submit(()->{
            try{
                var dictionary=snapshot.execute(tx->new Dictionary(List.copyOf(mapper.codes()),List.copyOf(mapper.areas()),List.copyOf(mapper.vendors())));
                String text=parser.extract(bytes,filename);
                if(Thread.currentThread().isInterrupted())throw ResumeDocumentParser.limit();
                return extractor.extract(text,target,dictionary,LocalDate.now(clock.withZone(TimeConfiguration.SEOUL)));
            }finally{Arrays.fill(bytes,(byte)0);}
        });}catch(RejectedExecutionException e){Arrays.fill(bytes,(byte)0);throw ResumeDocumentParser.error(429,"추출 요청이 많습니다. 잠시 후 다시 시도해 주세요.");}
        try{return work.get(30,TimeUnit.SECONDS);}
        catch(InterruptedException e){work.cancel(true);Arrays.fill(bytes,(byte)0);Thread.currentThread().interrupt();throw ResumeDocumentParser.limit();}
        catch(TimeoutException e){work.cancel(true);Arrays.fill(bytes,(byte)0);throw ResumeDocumentParser.limit();}
        catch(ExecutionException e){if(e.getCause() instanceof com.sicost.common.api.ApiException a)throw a;throw ResumeDocumentParser.error(422,"이력서를 읽지 못했습니다. 기존 입력을 유지하고 직접 입력하거나 다시 시도해 주세요.");}
    }
}
@RestController
class ResumeExtractionController {
    private final ResumeExtractionService service;
    ResumeExtractionController(ResumeExtractionService service){this.service=service;}
    @PostMapping(value="/api/resume-extractions",name="createResumeExtraction",consumes="multipart/form-data")
    ApiResponse<Extraction> extract(MultipartHttpServletRequest request)throws java.io.IOException, jakarta.servlet.ServletException {
        var parts=request.getParts();
        request.getParameterMap().keySet().forEach(key->{if(!key.equals("targetType"))throw ResumeDocumentParser.error(400,"file과 targetType만 입력해 주세요.");});if(parts.size()!=2||parts.stream().anyMatch(p->!Set.of("file","targetType").contains(p.getName()))||request.getFiles("file").size()!=1||request.getParameterValues("targetType")==null||request.getParameterValues("targetType").length!=1)
            throw ResumeDocumentParser.error(400,"단일 file과 targetType만 입력해 주세요.");
        var file=request.getFile("file");if(file==null||file.isEmpty())throw ResumeDocumentParser.error(400,"파일을 선택해 주세요.");if(file.getSize()>ResumeDocumentParser.MAX_BYTES)throw ResumeDocumentParser.error(413,"파일은 10MiB 이하로 선택해 주세요.");
        return ApiResponse.ok(service.extract(file.getBytes(),file.getOriginalFilename(),request.getParameter("targetType")));
    }
}
