package com.sicost.notificationsops;

import com.sicost.common.api.ApiException;
import com.sicost.common.api.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import static com.sicost.notificationsops.NotificationDtos.*;

@RestController
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service) { this.service=service; }
    @GetMapping("/api/notifications/unread-count")
    public ApiResponse<UnreadCount> getNotificationUnreadCount(@RequestParam Map<String,String> query) {
        noQuery(query);return ApiResponse.ok(service.getNotificationUnreadCount());
    }
    @PostMapping("/api/notifications/{id}/read")
    public ApiResponse<ReadResult> saveNotificationRead(@PathVariable String id,@RequestParam Map<String,String> query,
                                                       @RequestBody(required=false) byte[] body) {
        noQuery(query);noBody(body);return ApiResponse.ok(service.saveNotificationRead(id));
    }
    @PostMapping("/api/notifications/read-all")
    public ApiResponse<ReadAllResult> saveNotificationsReadAll(@RequestParam Map<String,String> query,
                                                             @RequestBody(required=false) byte[] body) {
        noQuery(query);noBody(body);return ApiResponse.ok(service.saveNotificationsReadAll());
    }
    private static void noQuery(Map<String,String> query) {
        if(!query.isEmpty())throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 조회 항목입니다.",query.keySet().iterator().next(),null);
    }
    private static void noBody(byte[] body) {
        if(body!=null&&body.length>0)throw new ApiException(400,"UNKNOWN_FIELD","알림 읽음 요청에는 본문을 입력할 수 없습니다.","body",null);
    }
}
