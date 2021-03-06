<?xml version="1.0" encoding="UTF-8"?>
<!--

    Copyright 2015 Red Hat, Inc. and/or its affiliates
    and other contributors as indicated by the @author tags.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

-->

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xalan="http://xml.apache.org/xalan"
  xmlns:ds="urn:jboss:domain:datasources:3.0" xmlns:ra="urn:jboss:domain:resource-adapters:3.0" xmlns:ejb3="urn:jboss:domain:ejb3:3.0"
  xmlns:logging="urn:jboss:domain:logging:3.0" xmlns:undertow="urn:jboss:domain:undertow:2.0" xmlns:tx="urn:jboss:domain:transactions:3.0"
  version="2.0" exclude-result-prefixes="xalan ds ra ejb3 logging undertow tx">

  <!-- will indicate if this is a "dev" build or "production" build -->
  <xsl:param name="kettle.build.type" />
  <xsl:param name="uuid.hawkular.accounts.backend" />

  <xsl:output method="xml" version="1.0" encoding="UTF-8" indent="yes" xalan:indent-amount="4" standalone="no" />
  <xsl:strip-space elements="*" />


  <xsl:template match="node()[name(.)='system-properties']">
    <xsl:copy>
      <xsl:apply-templates select="node()|@*"/>
      <property>
        <xsl:attribute name="name">hawkular.metrics.waitForService</xsl:attribute>
        <xsl:attribute name="value">&#36;{hawkular.metrics.waitForService:true}</xsl:attribute>
      </property>
    </xsl:copy>
  </xsl:template>

  <!-- Add the Agent Extension -->
  <xsl:template match="node()[name(.)='extensions']">
    <xsl:copy>
      <xsl:apply-templates select="node()|@*"/>
      <extension module="org.hawkular.agent"/><xsl:comment>Transformed</xsl:comment>
    </xsl:copy>
  </xsl:template>

  <!-- //*[local-name()='secure-deployment'] is an xPath's 1.0 way of saying of xPath's 2.0 prefix-less selector //*:secure-deployment  -->
  <xsl:template match="//*[*[local-name()='secure-deployment']]">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()" />
      <secure-deployment name="hawkular-inventory-dist.war">
        <realm>hawkular</realm>
        <resource>hawkular-accounts-backend</resource>
        <use-resource-role-mappings>true</use-resource-role-mappings>
        <enable-cors>true</enable-cors>
        <enable-basic-auth>true</enable-basic-auth>
        <!-- copy the secret value from the previous available secure-deployment -->
        <credential name="secret"><xsl:value-of select="*[local-name()='secure-deployment']/*[local-name()='credential' and @name='secret']/text()"/></credential>
      </secure-deployment>
      <secure-deployment name="hawkular-metrics-api-jaxrs.war">
        <realm>hawkular</realm>
        <resource>hawkular-accounts-backend</resource>
        <use-resource-role-mappings>true</use-resource-role-mappings>
        <enable-cors>true</enable-cors>
        <enable-basic-auth>true</enable-basic-auth>
        <!-- copy the secret value from the previous available secure-deployment -->
        <credential name="secret"><xsl:value-of select="*[local-name()='secure-deployment']/*[local-name()='credential' and @name='secret']/text()"/></credential>
      </secure-deployment>
      <secure-deployment name="hawkular-command-gateway-war.war">
        <realm>hawkular</realm>
        <resource>hawkular-accounts-backend</resource>
        <use-resource-role-mappings>true</use-resource-role-mappings>
        <enable-cors>true</enable-cors>
        <enable-basic-auth>true</enable-basic-auth>
        <!-- copy the secret value from the previous available secure-deployment -->
        <credential name="secret"><xsl:value-of select="*[local-name()='secure-deployment']/*[local-name()='credential' and @name='secret']/text()"/></credential>
      </secure-deployment>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="//*[local-name()='subsystem']/*[local-name()='server' and @name='default']">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
      <jms-topic name="HawkularInventoryChanges" entries="java:/topic/HawkularInventoryChanges"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="node()[name(.)='profile']">
    <xsl:copy>
      <xsl:apply-templates select="node()|@*"/>

      <!-- Hawkular WildFly Agent subsystem -->
      <subsystem xmlns="urn:org.hawkular.agent:agent:1.0"
                 enabled="true">

        <diagnostics enabled="true"
                     reportTo="LOG"
                     interval="1"
                     timeUnits="minutes"/>

        <storage-adapter type="HAWKULAR" username="jdoe" password="password" />

        <metric-set-dmr name="WildFly Memory Metrics" enabled="true">
          <metric-dmr name="Heap Used"
                      interval="30"
                      timeUnits="seconds"
                      metricUnits="bytes"
                      path="/core-service=platform-mbean/type=memory"
                      attribute="heap-memory-usage#used" />
          <metric-dmr name="Heap Committed"
                      interval="1"
                      timeUnits="minutes"
                      path="/core-service=platform-mbean/type=memory"
                      attribute="heap-memory-usage#committed" />
          <metric-dmr name="Heap Max"
                      interval="1"
                      timeUnits="minutes"
                      path="/core-service=platform-mbean/type=memory"
                      attribute="heap-memory-usage#max" />
          <metric-dmr name="NonHeap Used"
                      interval="30"
                      timeUnits="seconds"
                      path="/core-service=platform-mbean/type=memory"
                      attribute="non-heap-memory-usage#used" />
          <metric-dmr name="NonHeap Committed"
                      interval="1"
                      timeUnits="minutes"
                      path="/core-service=platform-mbean/type=memory"
                      attribute="non-heap-memory-usage#committed" />
          <metric-dmr name="Accumulated GC Duration"
                      metricType="counter"
                      interval="1"
                      timeUnits="minutes"
                      path="/core-service=platform-mbean/type=garbage-collector/name=*"
                      attribute="collection-time" />

        </metric-set-dmr>

        <metric-set-dmr name="WildFly Threading Metrics" enabled="true">
          <metric-dmr name="Thread Count"
                      interval="2"
                      timeUnits="minutes"
                      metricUnits="none"
                      path="/core-service=platform-mbean/type=threading"
                      attribute="thread-count" />
        </metric-set-dmr>

        <metric-set-dmr name="WildFly Aggregated Web Metrics" enabled="true">
          <metric-dmr name="Aggregated Active Web Sessions"
                      interval="1"
                      timeUnits="minutes"
                      path="/deployment=*/subsystem=undertow"
                      attribute="active-sessions" />
          <metric-dmr name="Aggregated Max Active Web Sessions"
                      interval="1"
                      timeUnits="minutes"
                      path="/deployment=*/subsystem=undertow"
                      attribute="max-active-sessions" />
          <metric-dmr name="Aggregated Expired Web Sessions"
                      metricType="counter"
                      interval="1"
                      timeUnits="minutes"
                      path="/deployment=*/subsystem=undertow"
                      attribute="expired-sessions" />
          <metric-dmr name="Aggregated Rejected Web Sessions"
                      metricType="counter"
                      interval="1"
                      timeUnits="minutes"
                      path="/deployment=*/subsystem=undertow"
                      attribute="rejected-sessions" />
          <metric-dmr name="Aggregated Servlet Request Time"
                      metricType="counter"
                      interval="1"
                      timeUnits="minutes"
                      path="/deployment=*/subsystem=undertow/servlet=*"
                      attribute="total-request-time" />
          <metric-dmr name="Aggregated Servlet Request Count"
                      metricType="counter"
                      interval="1"
                      timeUnits="minutes"
                      path="/deployment=*/subsystem=undertow/servlet=*"
                      attribute="request-count" />
        </metric-set-dmr>

        <metric-set-dmr name="Undertow Metrics" enabled="true">
          <metric-dmr name="Active Sessions"
                      interval="2"
                      timeUnits="minutes"
                      path="/subsystem=undertow"
                      attribute="active-sessions" />
          <metric-dmr name="Sessions Created"
                      metricType="counter"
                      interval="2"
                      timeUnits="minutes"
                      path="/subsystem=undertow"
                      attribute="sessions-created" />
          <metric-dmr name="Expired Sessions"
                      metricType="counter"
                      interval="2"
                      timeUnits="minutes"
                      path="/subsystem=undertow"
                      attribute="expired-sessions" />
          <metric-dmr name="Rejected Sessions"
                      metricType="counter"
                      interval="2"
                      timeUnits="minutes"
                      path="/subsystem=undertow"
                      attribute="rejected-sessions" />
          <metric-dmr name="Max Active Sessions"
                      interval="2"
                      timeUnits="minutes"
                      path="/subsystem=undertow"
                      attribute="max-active-sessions" />
        </metric-set-dmr>

        <metric-set-dmr name="Servlet Metrics" enabled="true">
          <metric-dmr name="Max Request Time"
                      interval="5"
                      timeUnits="minutes"
                      metricUnits="milliseconds"
                      path="/"
                      attribute="max-request-time" />
          <metric-dmr name="Min Request Time"
                      interval="5"
                      timeUnits="minutes"
                      path="/"
                      attribute="min-request-time" />
          <metric-dmr name="Total Request Time"
                      metricType="counter"
                      interval="5"
                      timeUnits="minutes"
                      path="/"
                      attribute="total-request-time" />
          <metric-dmr name="Request Count"
                      metricType="counter"
                      interval="5"
                      timeUnits="minutes"
                      path="/"
                      attribute="request-count" />
        </metric-set-dmr>

        <metric-set-dmr name="Singleton EJB Metrics" enabled="true">
          <metric-dmr name="Execution Time"
                      interval="5"
                      timeUnits="minutes"
                      path="/"
                      attribute="execution-time" />
          <metric-dmr name="Invocations"
                      metricType="counter"
                      interval="5"
                      timeUnits="minutes"
                      path="/"
                      attribute="invocations" />
          <metric-dmr name="Peak Concurrent Invocations"
                      interval="5"
                      timeUnits="minutes"
                      path="/"
                      attribute="peak-concurrent-invocations" />
          <metric-dmr name="Wait Time"
                      interval="5"
                      timeUnits="minutes"
                      path="/"
                      attribute="wait-time" />
        </metric-set-dmr>

        <metric-set-dmr name="Message Driven EJB Metrics" enabled="true">
          <metric-dmr name="Execution Time"
                      interval="5"
                      timeUnits="minutes"
                      path="/"
                      attribute="execution-time" />
          <metric-dmr name="Invocations"
                      metricType="counter"
                      interval="5"
                      timeUnits="minutes"
                      path="/"
                      attribute="invocations" />
          <metric-dmr name="Peak Concurrent Invocations"
                      interval="5"
                      timeUnits="minutes"
                      path="/"
                      attribute="peak-concurrent-invocations" />
          <metric-dmr name="Wait Time"
                      interval="5"
                      timeUnits="minutes"
                      path="/"
                      attribute="wait-time" />
          <metric-dmr name="Pool Available Count"
                      interval="5"
                      timeUnits="minutes"
                      path="/"
                      attribute="pool-available-count" />
          <metric-dmr name="Pool Create Count"
                      interval="5"
                      timeUnits="minutes"
                      path="/"
                      attribute="pool-create-count" />
          <metric-dmr name="Pool Current Size"
                      interval="5"
                      timeUnits="minutes"
                      path="/"
                      attribute="pool-current-size" />
          <metric-dmr name="Pool Max Size"
                      interval="5"
                      timeUnits="minutes"
                      path="/"
                      attribute="pool-max-size" />
          <metric-dmr name="Pool Remove Count"
                      interval="5"
                      timeUnits="minutes"
                      path="/"
                      attribute="pool-remove-count" />
        </metric-set-dmr>

        <metric-set-dmr name="Stateless Session EJB Metrics" enabled="true">
          <metric-dmr name="Execution Time"
                      interval="5"
                      timeUnits="minutes"
                      path="/"
                      attribute="execution-time" />
          <metric-dmr name="Invocations"
                      metricType="counter"
                      interval="5"
                      timeUnits="minutes"
                      path="/"
                      attribute="invocations" />
          <metric-dmr name="Peak Concurrent Invocations"
                      interval="5"
                      timeUnits="minutes"
                      path="/"
                      attribute="peak-concurrent-invocations" />
          <metric-dmr name="Wait Time"
                      interval="5"
                      timeUnits="minutes"
                      path="/"
                      attribute="wait-time" />
          <metric-dmr name="Pool Availabile Count"
                      interval="5"
                      timeUnits="minutes"
                      path="/"
                      attribute="pool-available-count" />
          <metric-dmr name="Pool Create Count"
                      interval="5"
                      timeUnits="minutes"
                      path="/"
                      attribute="pool-create-count" />
          <metric-dmr name="Pool Current Size"
                      interval="5"
                      timeUnits="minutes"
                      path="/"
                      attribute="pool-current-size" />
          <metric-dmr name="Pool Max Size"
                      interval="5"
                      timeUnits="minutes"
                      path="/"
                      attribute="pool-max-size" />
          <metric-dmr name="Pool Remove Count"
                      interval="5"
                      timeUnits="minutes"
                      path="/"
                      attribute="pool-remove-count" />
        </metric-set-dmr>

        <metric-set-dmr name="Datasource JDBC Metrics" enabled="true">
          <metric-dmr name="Prepared Statement Cache Access Count"
                      interval="10"
                      timeUnits="minutes"
                      path="/statistics=jdbc"
                      attribute="PreparedStatementCacheAccessCount" />
          <metric-dmr name="Prepared Statement Cache Add Count"
                      interval="10"
                      timeUnits="minutes"
                      path="/statistics=jdbc"
                      attribute="PreparedStatementCacheAddCount" />
          <metric-dmr name="Prepared Statement Cache Current Size"
                      interval="10"
                      timeUnits="minutes"
                      path="/statistics=jdbc"
                      attribute="PreparedStatementCacheCurrentSize" />
          <metric-dmr name="Prepared Statement Cache Delete Count"
                      interval="10"
                      timeUnits="minutes"
                      path="/statistics=jdbc"
                      attribute="PreparedStatementCacheDeleteCount" />
          <metric-dmr name="Prepared Statement Cache Hit Count"
                      interval="10"
                      timeUnits="minutes"
                      path="/statistics=jdbc"
                      attribute="PreparedStatementCacheHitCount" />
          <metric-dmr name="Prepared Statement Cache Miss Count"
                      interval="10"
                      timeUnits="minutes"
                      path="/statistics=jdbc"
                      attribute="PreparedStatementCacheMissCount" />
        </metric-set-dmr>

        <metric-set-dmr name="Datasource Pool Metrics" enabled="true">
          <metric-dmr name="Active Count"
                      interval="10"
                      timeUnits="minutes"
                      path="/statistics=pool"
                      attribute="ActiveCount" />
          <metric-dmr name="Available Count"
                      interval="1"
                      timeUnits="minutes"
                      path="/statistics=pool"
                      attribute="AvailableCount" />
          <metric-dmr name="Average Blocking Time"
                      interval="1"
                      timeUnits="minutes"
                      path="/statistics=pool"
                      attribute="AverageBlockingTime" />
          <metric-dmr name="Average Creation Time"
                      interval="1"
                      timeUnits="minutes"
                      path="/statistics=pool"
                      attribute="AverageCreationTime" />
          <metric-dmr name="Average Get Time"
                      interval="1"
                      timeUnits="minutes"
                      path="/statistics=pool"
                      attribute="AverageGetTime" />
          <metric-dmr name="Blocking Failure Count"
                      interval="10"
                      timeUnits="minutes"
                      path="/statistics=pool"
                      attribute="BlockingFailureCount" />
          <metric-dmr name="Created Count"
                      interval="10"
                      timeUnits="minutes"
                      path="/statistics=pool"
                      attribute="CreatedCount" />
          <metric-dmr name="Destroyed Count"
                      interval="10"
                      timeUnits="minutes"
                      path="/statistics=pool"
                      attribute="DestroyedCount" />
          <metric-dmr name="Idle Count"
                      interval="10"
                      timeUnits="minutes"
                      path="/statistics=pool"
                      attribute="IdleCount" />
          <metric-dmr name="In Use Count"
                      interval="1"
                      timeUnits="minutes"
                      path="/statistics=pool"
                      attribute="InUseCount" />
          <metric-dmr name="Max Creation Time"
                      interval="10"
                      timeUnits="minutes"
                      path="/statistics=pool"
                      attribute="MaxCreationTime" />
          <metric-dmr name="Max Get Time"
                      interval="10"
                      timeUnits="minutes"
                      path="/statistics=pool"
                      attribute="MaxGetTime" />
          <metric-dmr name="Max Used Count"
                      interval="10"
                      timeUnits="minutes"
                      path="/statistics=pool"
                      attribute="MaxUsedCount" />
          <metric-dmr name="Max Wait Count"
                      interval="10"
                      timeUnits="minutes"
                      path="/statistics=pool"
                      attribute="MaxWaitCount" />
          <metric-dmr name="Max Wait Time"
                      interval="10"
                      timeUnits="minutes"
                      path="/statistics=pool"
                      attribute="MaxWaitTime" />
          <metric-dmr name="Timed Out"
                      interval="1"
                      timeUnits="minutes"
                      path="/statistics=pool"
                      attribute="TimedOut" />
          <metric-dmr name="Total Blocking Time"
                      interval="10"
                      timeUnits="minutes"
                      path="/statistics=pool"
                      attribute="TotalBlockingTime" />
          <metric-dmr name="Total Creation Time"
                      interval="10"
                      timeUnits="minutes"
                      path="/statistics=pool"
                      attribute="TotalCreationTime" />
          <metric-dmr name="Total Get Time"
                      interval="10"
                      timeUnits="minutes"
                      path="/statistics=pool"
                      attribute="TotalGetTime" />
          <metric-dmr name="Wait Count"
                      interval="10"
                      timeUnits="minutes"
                      path="/statistics=pool"
                      attribute="WaitCount" />
        </metric-set-dmr>

        <metric-set-dmr name="Transactions Metrics" enabled="true">
          <metric-dmr name="Number of Aborted Transactions"
                      metricType="counter"
                      interval="10"
                      timeUnits="minutes"
                      path="/"
                      attribute="number-of-aborted-transactions" />
          <metric-dmr name="Number of Application Rollbacks"
                      metricType="counter"
                      interval="10"
                      timeUnits="minutes"
                      path="/"
                      attribute="number-of-application-rollbacks" />
          <metric-dmr name="Number of Committed Transactions"
                      metricType="counter"
                      interval="10"
                      timeUnits="minutes"
                      path="/"
                      attribute="number-of-committed-transactions" />
          <metric-dmr name="Number of Heuristics"
                      metricType="counter"
                      interval="10"
                      timeUnits="minutes"
                      path="/"
                      attribute="number-of-heuristics" />
          <metric-dmr name="Number of In-Flight Transactions"
                      interval="10"
                      timeUnits="minutes"
                      path="/"
                      attribute="number-of-inflight-transactions" />
          <metric-dmr name="Number of Nested Transactions"
                      interval="10"
                      timeUnits="minutes"
                      path="/"
                      attribute="number-of-nested-transactions" />
          <metric-dmr name="Number of Resource Rollbacks"
                      metricType="counter"
                      interval="10"
                      timeUnits="minutes"
                      path="/"
                      attribute="number-of-resource-rollbacks" />
          <metric-dmr name="Number of Timed Out Transactions"
                      metricType="counter"
                      interval="10"
                      timeUnits="minutes"
                      path="/"
                      attribute="number-of-timed-out-transactions" />
          <metric-dmr name="Number of Transactions"
                      interval="10"
                      timeUnits="minutes"
                      path="/"
                      attribute="number-of-transactions" />
        </metric-set-dmr>

        <avail-set-dmr name="Server Availability" enabled="true">
          <avail-dmr name="App Server"
                     interval="30"
                     timeUnits="seconds"
                     path="/"
                     attribute="server-state"
                     upRegex="run.*" />
        </avail-set-dmr>

        <avail-set-dmr name="Deployment Status" enabled="true">
          <avail-dmr name="Deployment Status"
                     interval="1"
                     timeUnits="minutes"
                     path="/"
                     attribute="status"
                     upRegex="OK" />
        </avail-set-dmr>

        <resource-type-set-dmr name="Main" enabled="true">
          <resource-type-dmr name="WildFly Server"
                             resourceNameTemplate="WildFly Server [%ManagedServerName] [${{jboss.node.name:localhost}}]"
                             path="/"
                             metricSets="WildFly Memory Metrics,WildFly Threading Metrics,WildFly Aggregated Web Metrics"
                             availSets="Server Availability">
            <resource-config-dmr name="Hostname"
                                 path="/core-service=server-environment"
                                 attribute="qualified-host-name" />
            <resource-config-dmr name="Version"
                                 attribute="release-version" />
            <resource-config-dmr name="Product Name"
                                 attribute="product-name" />
            <resource-config-dmr name="Bound Address"
                                 path="/socket-binding-group=standard-sockets/socket-binding=http"
                                 attribute="bound-address" />
            <operation-dmr name="JDR" operationName="generate-jdr-report" path="/subsystem=jdr" />
          </resource-type-dmr>
        </resource-type-set-dmr>

        <resource-type-set-dmr name="Hawkular" enabled="true">
          <resource-type-dmr name="Hawkular WildFly Agent"
                             resourceNameTemplate="Hawkular WildFly Agent"
                             path="/subsystem=hawkular-wildfly-agent"
                             parents="WildFly Server">
            <operation-dmr name="Status"                   operationName="status" />
            <operation-dmr name="Inventory Discovery Scan" operationName="fullDiscoveryScan" />
          </resource-type-dmr>
        </resource-type-set-dmr>

         <resource-type-set-dmr name="Deployment" enabled="true">
            <resource-type-dmr name="Deployment"
                               resourceNameTemplate="Deployment [%2]"
                               path="/deployment=*"
                               parents="WildFly Server"
                               metricSets="Undertow Metrics"
                               availSets="Deployment Status">
              <operation-dmr name="Deploy" operationName="deploy" path="/" />
              <operation-dmr name="Redeploy" operationName="redeploy" path="/" />
              <operation-dmr name="Remove" operationName="remove" path="/" />
              <operation-dmr name="Undeploy" operationName="undeploy" path="/" />
            </resource-type-dmr>

            <resource-type-dmr name="SubDeployment"
                               resourceNameTemplate="SubDeployment [%-]"
                               path="/subdeployment=*"
                               parents="Deployment"
                               metricSets="Undertow Metrics">
            </resource-type-dmr>
         </resource-type-set-dmr>

        <resource-type-set-dmr name="Web Component" enabled="true">
          <resource-type-dmr name="Servlet"
                             resourceNameTemplate="Servlet [%-]"
                             path="/subsystem=undertow/servlet=*"
                             parents="Deployment,SubDeployment"
                             metricSets="Servlet Metrics" />
        </resource-type-set-dmr>

        <resource-type-set-dmr name="EJB" enabled="true">
          <resource-type-dmr name="Singleton EJB"
                             resourceNameTemplate="Singleton EJB [%-]"
                             path="/subsystem=ejb3/singleton-bean=*"
                             parents="Deployment,SubDeployment"
                             metricSets="Singleton EJB Metrics" />

          <resource-type-dmr name="Message Driven EJB"
                             resourceNameTemplate="Message Driven EJB [%-]"
                             path="/subsystem=ejb3/message-driven-bean=*"
                             parents="Deployment,SubDeployment"
                             metricSets="Message Driven EJB Metrics" />

          <resource-type-dmr name="Stateless Session EJB"
                             resourceNameTemplate="Stateless Session EJB [%-]"
                             path="/subsystem=ejb3/stateless-session-bean=*"
                             parents="Deployment,SubDeployment"
                             metricSets="Stateless Session EJB Metrics" />
        </resource-type-set-dmr>

        <resource-type-set-dmr name="Datasource" enabled="true">
          <resource-type-dmr name="Datasource"
                             resourceNameTemplate="Datasource [%-]"
                             path="/subsystem=datasources/data-source=*"
                             parents="WildFly Server"
                             metricSets="Datasource Pool Metrics,Datasource JDBC Metrics">
            <resource-config-dmr name="Connection URL"   attribute="connection-url" />
            <resource-config-dmr name="Driver Name"      attribute="driver-name" />
            <resource-config-dmr name="Driver Class"     attribute="driver-class" />
            <resource-config-dmr name="Datasource Class" attribute="datasource-class" />
            <resource-config-dmr name="Enabled"          attribute="enabled" />
            <resource-config-dmr name="JNDI Name"        attribute="jndi-name" />
            <resource-config-dmr name="Username"         attribute="user-name" />
            <resource-config-dmr name="Password"         attribute="password" />
            <resource-config-dmr name="Security Domain"  attribute="security-domain" />
            <resource-config-dmr name="Connection Properties"
                                 path="/connection-properties=*"
                                 attribute="value" />
          </resource-type-dmr>
        </resource-type-set-dmr>

        <resource-type-set-dmr name="XA Datasource" enabled="true">
          <resource-type-dmr name="XA Datasource"
                             resourceNameTemplate="XA Datasource [%-]"
                             path="/subsystem=datasources/xa-data-source=*"
                             parents="WildFly Server"
                             metricSets="Datasource Pool Metrics,Datasource JDBC Metrics">
            <resource-config-dmr name="Driver Name"         attribute="driver-name" />
            <resource-config-dmr name="XA Datasource Class" attribute="xa-datasource-class" />
            <resource-config-dmr name="Enabled"             attribute="enabled" />
            <resource-config-dmr name="JNDI Name"           attribute="jndi-name" />
            <resource-config-dmr name="Username"            attribute="user-name" />
            <resource-config-dmr name="Password"            attribute="password" />
            <resource-config-dmr name="Security Domain"     attribute="security-domain" />
            <resource-config-dmr name="Datasource Properties"
                                 path="/xa-datasource-properties=*"
                                 attribute="value" />
          </resource-type-dmr>
        </resource-type-set-dmr>

        <resource-type-set-dmr name="JDBC Driver" enabled="true">
          <resource-type-dmr name="JDBC Driver"
                             resourceNameTemplate="JDBC Driver [%-]"
                             path="/subsystem=datasources/jdbc-driver=*"
                             parents="WildFly Server" />
        </resource-type-set-dmr>

        <resource-type-set-dmr name="Transaction Manager" enabled="true">
          <resource-type-dmr name="Transaction Manager"
                             resourceNameTemplate="Transaction Manager"
                             path="/subsystem=transactions"
                             parents="WildFly Server"
                             metricSets="Transactions Metrics" />
        </resource-type-set-dmr>

        <managed-servers>
          <remote-dmr name="Another Remote Server"
                      enabled="false"
                      host="localhost"
                      port="9990"
                      username="adminUser"
                      password="adminPass"
                      resourceTypeSets="Main,Deployment,Web Component,EJB,Datasource,XA Datasource,JDBC Driver,Transaction Manager" />

          <local-dmr name="Local"
                     enabled="true"
                     resourceTypeSets="Main,Deployment,Web Component,EJB,Datasource,XA Datasource,JDBC Driver,Transaction Manager,Hawkular" />

        </managed-servers>

      </subsystem>
    </xsl:copy>
  </xsl:template>

  <!-- copy everything else as-is -->
  <xsl:template match="node()|@*">
    <xsl:copy>
      <xsl:apply-templates select="node()|@*" />
    </xsl:copy>
  </xsl:template>

</xsl:stylesheet>
