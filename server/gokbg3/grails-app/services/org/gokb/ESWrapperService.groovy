package org.gokb

import groovy.json.JsonSlurper
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.grails.web.json.parser.JSONParser

import static groovy.json.JsonOutput.*

class ESWrapperService {

  static transactional = false
  def grailsApplication
  TransportClient esclient = null

  @javax.annotation.PostConstruct
  def init() {
    log.debug("init ES wrapper service");
  }


  def getSettings() {
    JSONParser jsonParser = new JSONParser(this.class.classLoader.getResourceAsStream("${File.separator}elasticsearch${File.separator}es_settings.json"))

    jsonParser.parse()
  }


  def getMapping() {
    JSONParser jsonParser = new JSONParser(this.class.classLoader.getResourceAsStream("${File.separator}elasticsearch${File.separator}es_mapping.json"))

    jsonParser.parse()
  }


  private def ensureClient() {
    if ( esclient == null ) {
      def es_cluster_name = grailsApplication.config?.gokb?.es?.cluster ?: 'elasticsearch'
      def es_host_name = grailsApplication.config?.gokb?.es?.host ?: 'localhost'

      log.debug("esclient is null, creating now... host: ${es_host_name} cluster:${es_cluster_name}");

      log.debug("Looking for es on host ${es_host_name} with cluster name ${es_cluster_name}");

      Settings settings = Settings.builder().put("cluster.name", es_cluster_name).build();
      esclient = new org.elasticsearch.transport.client.PreBuiltTransportClient(settings);
      esclient.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(es_host_name), 9300));

      log.debug("ES wrapper service init completed OK");
    }
    esclient
  }


  def index(index,typename,record_id, record) {
    log.debug("indexing ... ${typename},${record_id},...")
    def result=null
    try {
      def future = ensureClient().prepareIndex(index,typename,record_id).setSource(record)
      result=future.get()
    }
    catch ( Exception e ) {
      log.error("Error processing ${toJson(record)}",e)
      e.printStackTrace()
    }
    log.debug("indexing complete")
    result
  }


  def getClient() {
    return ensureClient()
  }


  @javax.annotation.PreDestroy
  def destroy() {
    log.debug("Close Elasticsearch client.")
    esclient.close()
  }

}
