package org.olf

import com.k_int.okapi.OkapiHeaders
import com.k_int.okapi.OkapiTenantResolver
import com.k_int.web.toolkit.testing.HttpSpec
import grails.gorm.multitenancy.Tenants
import spock.lang.Stepwise
import spock.util.concurrent.PollingConditions
import spock.lang.Ignore
import com.k_int.web.toolkit.utils.GormUtils

@Stepwise
abstract class BaseSpec extends HttpSpec {
  def setupSpec() {
    httpClientConfig = {
      client.clientCustomizer { HttpURLConnection conn ->
        conn.connectTimeout = 3000
        conn.readTimeout = 20000
      }
    }
    addDefaultHeaders(
      (OkapiHeaders.TENANT): "${this.class.simpleName}",
      (OkapiHeaders.USER_ID): "${this.class.simpleName}_user"
    ) 
  }
  
  Map<String, String> getAllHeaders() {
    state.specDefaultHeaders + headersOverride
  }
  
  String getCurrentTenant() {
    allHeaders?.get(OkapiHeaders.TENANT)
  }

  final String getTenantId() {
    currentTenant.toLowerCase()
  }

  void 'Pre purge tenant' () {
    boolean resp = false
    when: 'Purge the tenant'
      try {
        resp = doDelete('/_/tenant', null)
        resp = true
      } catch (Exception ex) { resp = true }
      
    then: 'Response obtained'
      resp == true
  }
  
  void 'Ensure test tenant' () {
    
    when: 'Create the tenant'
      def resp = doPost('/_/tenant', {
      parameters ([["key": "loadReference", "value": true]])
    })

    then: 'Response obtained'
    resp != null

    and: 'Refdata added'

      List list
      // Wait for the refdata to be loaded.
      def conditions = new PollingConditions(timeout: 10)
      conditions.eventually {
        (list = doGet('/servint/refdata')).size() > 0
      }
  }


  @Ignore
  def withTenant(Closure c) {
    Tenants.withId(OkapiTenantResolver.getTenantSchemaName( tenantId )) {
      c.call()
    }
  }

  @Ignore
  def withTenantNewTransaction(Closure c) {
    withTenant {
      GormUtils.withNewTransaction {
        c.call()
      }
    }
  }


}
