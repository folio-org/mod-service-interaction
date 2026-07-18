package org.folio.servint.service.dashboard;

import lombok.RequiredArgsConstructor;
import org.folio.servint.service.refdata.RefdataService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the DashboardAccess.Access controlled vocabulary — the legacy
 * module's kiwt @Defaults(['Manage', 'Edit', 'View']) seeding, run on
 * tenant reference-data load. Idempotent via lookupOrCreate.
 */
@Service
@RequiredArgsConstructor
public class DashboardSeedService {

  private final RefdataService refdata;

  @Transactional
  public void seed() {
    refdata.lookupOrCreate(DashboardService.CAT_ACCESS, "Manage", "manage");
    refdata.lookupOrCreate(DashboardService.CAT_ACCESS, "Edit", "edit");
    refdata.lookupOrCreate(DashboardService.CAT_ACCESS, "View", "view");
  }
}
