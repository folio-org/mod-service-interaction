package org.folio.servint.service.dashboard;

import lombok.RequiredArgsConstructor;
import org.folio.servint.domain.entity.ExternalUser;
import org.folio.servint.repository.ExternalUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Port of the legacy ExternalUserService: an ExternalUser row is a stub for
 * a FOLIO user, created on first sight with the FOLIO user UUID as its id.
 */
@Service
@RequiredArgsConstructor
public class ExternalUserService {

  private final ExternalUserRepository users;

  @Transactional
  public ExternalUser resolveUser(String uuid) {
    return users.findById(uuid).orElseGet(() -> {
      var user = new ExternalUser();
      user.setId(uuid);
      return users.saveAndFlush(user);
    });
  }
}
