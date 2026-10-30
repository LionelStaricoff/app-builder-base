package com.openlab.mx.movo.shared.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditRepository extends JpaRepository<AdminAuditLog, Long> {
}
