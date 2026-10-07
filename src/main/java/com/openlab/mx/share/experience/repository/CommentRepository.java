package com.openlab.mx.share.experience.repository;

import com.openlab.mx.share.experience.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByInstanceId(Long instanceId);

    Optional<Comment> findByInstanceIdAndUserId(Long instanceId, Long userId);

    boolean existsByInstanceIdAndUserId(Long instanceId, Long userId);
}
