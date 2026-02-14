import org.olf.security.NoOpServletFilter

// Place your Spring DSL code here
beans = {
  // Spring Security plugin still references this bean in Grails 7 upgrade path.
  sitemesh3Secured(NoOpServletFilter)
}
