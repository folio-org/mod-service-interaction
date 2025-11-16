package mod.service.interaction

import com.k_int.okapi.OkapiTenantAdminService
import org.springframework.security.web.FilterChainProxy;

class BootStrap {

  def grailsApplication
  def springSecurityFilterChain
  OkapiTenantAdminService okapiTenantAdminService

  def init = { servletContext ->

    log.info("${grailsApplication.getMetadata().getApplicationName()}  (${grailsApplication.config?.info?.app?.version}) initialising");
    log.info("          build number -> ${grailsApplication.metadata['build.number']}");
    log.info("        build revision -> ${grailsApplication.metadata['build.git.revision']}");
    log.info("          build branch -> ${grailsApplication.metadata['build.git.branch']}");
    log.info("          build commit -> ${grailsApplication.metadata['build.git.commit']}");
    log.info("            build time -> ${grailsApplication.metadata['build.time']}");
    log.info("            build host -> ${grailsApplication.metadata['build.host']}");
    log.info("         Base JDBC URL -> ${grailsApplication.config.dataSource.url}");

    // Check that the migrations file is present - it's absence indicates a build time failure
    def resFile = this.class.classLoader.getResource('module-tenant-changelog.groovy')
    if ( resFile == null ) {
      log.error("Resource /module-tenant-changelog.groovy not found. Critical error in build - service will not function properly");
    }

		if (springSecurityFilterChain instanceof FilterChainProxy) {
      log.info("== Spring Security Filter Chains ==")
      def ctx = grailsApplication.mainContext
      springSecurityFilterChain.filterChains.each { chain ->
        log.info("Pattern: ${chain.requestMatcher}")
        chain.filters.eachWithIndex { filter, i ->
	        def beanName = ctx.getBeanNamesForType(filter.class).find { ctx.getBean(it).is(filter) }
		      log.info("  ${i + 1}. ${beanName ?: filter.class.simpleName} -> ${filter.class.name}")
        }
      }
    }
  }


  def destroy = {
  }
}
