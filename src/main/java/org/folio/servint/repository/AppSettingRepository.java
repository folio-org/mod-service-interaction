package org.folio.servint.repository;

import org.folio.servint.domain.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppSettingRepository
    extends JpaRepository<AppSetting, String>, JpaSpecificationExecutor<AppSetting> {
}
