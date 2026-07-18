@Library ('folio_jenkins_shared_libs') _

// Maven build at the repo root (Spring Boot port). The descriptor templates in
// descriptors/ are filtered into target/ by the build; health is served by
// Spring Actuator at /admin/health on port 8081 (see application.yml).
buildMvn {
  publishModDescriptor = true
  mvnDeploy = true
  buildNode = 'jenkins-agent-java21'

  doDocker = {
    buildJavaDocker {
      publishMaster = true
      healthChk = true
      healthChkCmd = 'wget --no-verbose --tries=3 --spider http://localhost:8081/admin/health || exit 1'
    }
  }
}
