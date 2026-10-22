package com.skillmanagement.user.repository;

import com.skillmanagement.user.model.UserHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserHistoryRepository extends JpaRepository<UserHistory, Long> {
}
