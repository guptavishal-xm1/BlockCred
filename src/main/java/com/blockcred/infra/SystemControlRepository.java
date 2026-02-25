package com.blockcred.infra;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemControlRepository extends JpaRepository<SystemControlEntity, String> {
}
