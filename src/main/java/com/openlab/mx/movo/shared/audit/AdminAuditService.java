package com.openlab.mx.movo.shared.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AdminAuditService {

    private final AdminAuditRepository repo;

    public AdminAuditService(AdminAuditRepository repo) {
        this.repo = repo;
    }

    public void record(String action, String targetType, String targetId, String details) {
        String actor = currentUsername();
        repo.save(AdminAuditLog.builder()
                .actorUsername(actor)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .details(details)
                .build());
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return "system";
        }
        return auth.getName();
    }
}
