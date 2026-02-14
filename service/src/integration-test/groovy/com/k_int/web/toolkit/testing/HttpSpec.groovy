package com.k_int.web.toolkit.testing

import static groovy.transform.TypeCheckingMode.SKIP
import static groovyx.net.http.ContentTypes.JSON
import static groovyx.net.http.HttpBuilder.configure

import java.util.concurrent.Executors

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value

import grails.testing.mixin.integration.Integration
import grails.web.http.HttpHeaders
import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovyx.net.http.ChainedHttpConfig
import groovyx.net.http.FromServer
import groovyx.net.http.HttpBuilder
import groovyx.net.http.NativeHandlers
import spock.lang.Shared
import spock.lang.Specification

@CompileStatic
@Integration
abstract class HttpSpec extends Specification {
  static final Logger log = LoggerFactory.getLogger(HttpSpec)

  private static final List<String> EXTRA_JSON_TYPES = [
    'application/vnd.api+json'
  ]

  protected static class SpecState {
    Map<String, String> specDefaultHeaders = [
      (HttpHeaders.CONTENT_TYPE): 'application/json',
      (HttpHeaders.ACCEPT): 'application/json'
    ]
    Closure httpClientConfig
    HttpBuilder httpClient

    boolean setupSpecDone = false
    Closure cleanupClosure = null
  }

  @Shared
  protected SpecState state = new SpecState()

  protected void setHttpClientConfig(Closure c) {
    if (state.httpClient != null) throw new IllegalStateException("Cannot set client config after spec setup")
    state.httpClientConfig = c
  }

  protected Map<String, String> setDefaultHeaders(Map<String, String> defaultHeaders) {
    state.specDefaultHeaders = defaultHeaders
  }

  protected Map<String, String> addDefaultHeaders(Map<String, String> defaultHeaders) {
    state.specDefaultHeaders += defaultHeaders
  }

  Map<String, String> headersOverride = [:]
  protected Map<String, String> setHeaders(Map<String, String> vals) {
    headersOverride = vals
  }

  private String cleanUri(String uri) {
    if (uri.startsWith('//')) {
      uri = uri.substring(1)
    }
    uri
  }

  private def buildJson(def jsonObj) {
    if (!(jsonObj instanceof Closure)) {
      return jsonObj
    }
    JsonOutput.toJson(jsonObj)
  }

  protected def doGet(final String uri, final Map params = null, final Closure expand = null) {
    state.httpClient.get({
      request.uri = cleanUri(uri)
      request.uri.query = params
      request.headers = (state.specDefaultHeaders + headersOverride) as Map

      if (expand) {
        expand.rehydrate(delegate, expand.owner, thisObject)()
      }
    })
  }

  protected def doPost(final String uri, final def jsonData, final Map params = null, final Closure expand = null) {
    state.httpClient.post({
      request.uri = cleanUri(uri)
      request.uri.query = params
      request.body = buildJson(jsonData)
      request.headers = (state.specDefaultHeaders + headersOverride) as Map

      if (expand) {
        expand.rehydrate(delegate, expand.owner, thisObject)()
      }
    })
  }

  protected def doPut(final String uri, final def jsonData, final Map params = null, final Closure expand = null) {
    state.httpClient.put({
      request.uri = cleanUri(uri)
      request.uri.query = params
      request.body = buildJson(jsonData)
      request.headers = (state.specDefaultHeaders + headersOverride) as Map

      if (expand) {
        expand.rehydrate(delegate, expand.owner, thisObject)()
      }
    })
  }

  protected def doPatch(final String uri, final def jsonData, final Map params = null, final Closure expand = null) {
    state.httpClient.patch({
      request.uri = cleanUri(uri)
      request.uri.query = params
      request.body = buildJson(jsonData)
      request.headers = (state.specDefaultHeaders + headersOverride) as Map

      if (expand) {
        expand.rehydrate(delegate, expand.owner, thisObject)()
      }
    })
  }

  protected def doDelete(final String uri, final Map params = null, final Closure expand = null) {
    state.httpClient.delete({
      request.uri = cleanUri(uri)
      request.uri.query = params
      request.headers = (state.specDefaultHeaders + headersOverride) as Map

      if (expand) {
        expand.rehydrate(delegate, expand.owner, thisObject)()
      }
    })
  }

  @CompileStatic(SKIP)
  def setup() {
    if (!state.setupSpecDone) {
      setupSpecWithSpring()
      state.setupSpecDone = true
    }

    state.cleanupClosure = this.metaClass.respondsTo('cleanupSpecWithSpring') ? this.&cleanupSpecWithSpring : null
  }

  def cleanupSpec() {
    state.cleanupClosure?.run()
  }

  @Value('${server.servlet.context-path:/}')
  private String internalContextValue
  private String internalBaseUrl

  String getBaseUrl() {
    if (!internalBaseUrl) {
      internalBaseUrl = "http://localhost:${this.getAt('serverPort')}${internalContextValue}"
    }
    internalBaseUrl
  }

  def setupSpecWithSpring() {
    final String root = "$baseUrl"
    state.httpClient = configure {
      if (root) {
        log.info "Using default location for okapi at: ${root}"
        request.uri = root
      } else {
        log.info "No config options specifying okapiHost and okapiPort found on startup."
      }
      execution.executor = Executors.newSingleThreadExecutor()
      request.contentType = JSON[0]

      response.parser(HttpSpec.EXTRA_JSON_TYPES) { ChainedHttpConfig cfg, FromServer fs ->
        NativeHandlers.Parsers.json(cfg, fs)
      }

      client.clientCustomizer {
        HttpURLConnection conn = it as HttpURLConnection
        conn.connectTimeout = 2000
        conn.readTimeout = 3000
      }

      state.httpClientConfig?.rehydrate(delegate, owner, thisObject)?.call()
    }
  }
}
