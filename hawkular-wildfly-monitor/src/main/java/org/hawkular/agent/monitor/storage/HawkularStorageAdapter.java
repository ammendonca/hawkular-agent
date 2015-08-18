/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.agent.monitor.storage;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.hawkular.agent.monitor.api.Avail;
import org.hawkular.agent.monitor.api.AvailDataPayloadBuilder;
import org.hawkular.agent.monitor.api.MetricDataPayloadBuilder;
import org.hawkular.agent.monitor.diagnostics.Diagnostics;
import org.hawkular.agent.monitor.extension.MonitorServiceConfiguration;
import org.hawkular.agent.monitor.inventory.AvailInstance;
import org.hawkular.agent.monitor.inventory.AvailType;
import org.hawkular.agent.monitor.inventory.ID;
import org.hawkular.agent.monitor.inventory.MeasurementInstance;
import org.hawkular.agent.monitor.inventory.MeasurementType;
import org.hawkular.agent.monitor.inventory.MetricInstance;
import org.hawkular.agent.monitor.inventory.MetricType;
import org.hawkular.agent.monitor.inventory.NamedObject;
import org.hawkular.agent.monitor.inventory.Resource;
import org.hawkular.agent.monitor.inventory.ResourceConfigurationPropertyInstance;
import org.hawkular.agent.monitor.inventory.ResourceType;
import org.hawkular.agent.monitor.log.MsgLogger;
import org.hawkular.agent.monitor.scheduler.polling.Task;
import org.hawkular.agent.monitor.service.ServerIdentifiers;
import org.hawkular.agent.monitor.service.Util;
import org.hawkular.bus.restclient.RestClient;
import org.hawkular.inventory.api.Resources;
import org.hawkular.inventory.api.model.CanonicalPath;
import org.hawkular.inventory.api.model.MetricDataType;
import org.hawkular.inventory.api.model.MetricUnit;
import org.hawkular.inventory.api.model.StructuredData;
import org.hawkular.inventory.json.PathDeserializer;

import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

public class HawkularStorageAdapter implements StorageAdapter {
    private MonitorServiceConfiguration.StorageAdapter config;
    private Diagnostics diagnostics;
    private ServerIdentifiers selfId;
    private HttpClientBuilder httpClientBuilder;

    public HawkularStorageAdapter() {
    }

    private String getFeedId() {
        return this.selfId.getFullIdentifier();
    }

    @Override
    public void initialize(org.hawkular.agent.monitor.extension.MonitorServiceConfiguration.StorageAdapter config,
            Diagnostics diag, ServerIdentifiers selfId, HttpClientBuilder httpClientBuilder) {
        this.config = config;
        this.diagnostics = diag;
        this.selfId = selfId;
        this.httpClientBuilder = httpClientBuilder;
        PathDeserializer.setCurrentCanonicalOrigin(CanonicalPath.of().tenant(config.tenantId).get());
    }

    @Override
    public MonitorServiceConfiguration.StorageAdapter getStorageAdapterConfiguration() {
        return config;
    }

    @Override
    public MetricDataPayloadBuilder createMetricDataPayloadBuilder() {
        return new HawkularMetricDataPayloadBuilder();
    }

    @Override
    public AvailDataPayloadBuilder createAvailDataPayloadBuilder() {
        return new HawkularAvailDataPayloadBuilder();
    }

    @Override
    public void storeMetrics(Set<MetricDataPoint> datapoints) {
        if (datapoints == null || datapoints.isEmpty()) {
            return; // nothing to do
        }

        MetricDataPayloadBuilder payloadBuilder = createMetricDataPayloadBuilder();
        for (MetricDataPoint datapoint : datapoints) {
            Task task = datapoint.getTask();
            String key = task.getKeyGenerator().generateKey(task);
            long timestamp = datapoint.getTimestamp();
            double value = datapoint.getValue();
            payloadBuilder.addDataPoint(key, timestamp, value, datapoint.getMetricType());
        }

        store(payloadBuilder);

        return;
    }

    @Override
    public void store(MetricDataPayloadBuilder payloadBuilder) {

        String tenantId = this.config.tenantId;
        ((HawkularMetricDataPayloadBuilder) payloadBuilder).setTenantId(tenantId);

        // for now, we need to send it twice:
        // 1) directly to metrics for storage
        // 2) on the message bus for further processing

        // send to metrics
        MetricsOnlyStorageAdapter metricsAdapter = new MetricsOnlyStorageAdapter();
        metricsAdapter.initialize(getStorageAdapterConfiguration(), diagnostics, selfId, httpClientBuilder);
        metricsAdapter.store(((HawkularMetricDataPayloadBuilder) payloadBuilder)
                .toMetricsOnlyMetricDataPayloadBuilder());

        // send to bus
        String jsonPayload = null;
        try {
            // build the URL to the bus interface
            StringBuilder urlStr = Util.getContextUrlString(this.config.url, this.config.busContext);
            urlStr = Util.convertToNonSecureUrl(urlStr.toString());
            URL url = new URL(urlStr.toString());

            // build the bus client
            RestClient busClient = new RestClient(url);

            // send the message to the bus
            jsonPayload = payloadBuilder.toPayload().toString();
            busClient.postTopicMessage("HawkularMetricData", jsonPayload, null);

            // looks like everything stored successfully
            // the metrics storage adapter already did this, so don't duplicate the stats here
            //diagnostics.getMetricRate().mark(payloadBuilder.getNumberDataPoints());

        } catch (Throwable t) {
            MsgLogger.LOG.errorFailedToStoreMetricData(t, jsonPayload);
            diagnostics.getStorageErrorRate().mark(1);
        }
    }

    @Override
    public void storeAvails(Set<AvailDataPoint> datapoints) {
        if (datapoints == null || datapoints.isEmpty()) {
            return; // nothing to do
        }

        AvailDataPayloadBuilder payloadBuilder = createAvailDataPayloadBuilder();
        for (AvailDataPoint datapoint : datapoints) {
            Task task = datapoint.getTask();
            String key = task.getKeyGenerator().generateKey(task);
            long timestamp = datapoint.getTimestamp();
            Avail value = datapoint.getValue();
            payloadBuilder.addDataPoint(key, timestamp, value);
        }

        store(payloadBuilder);

        return;
    }

    @Override
    public void store(AvailDataPayloadBuilder payloadBuilder) {

        String tenantId = this.config.tenantId;
        ((HawkularAvailDataPayloadBuilder) payloadBuilder).setTenantId(tenantId);

        // for now, we need to send it twice:
        // 1) directly to h-metrics for storage
        // 2) on the message bus for further processing

        // send to h-metrics
        MetricsOnlyStorageAdapter metricsAdapter = new MetricsOnlyStorageAdapter();
        metricsAdapter.initialize(getStorageAdapterConfiguration(), diagnostics, selfId, httpClientBuilder);
        metricsAdapter.store(((HawkularAvailDataPayloadBuilder) payloadBuilder)
                .toMetricsOnlyAvailDataPayloadBuilder());

        // send to bus
        String jsonPayload = null;
        try {
            // build the URL to the bus interface
            StringBuilder urlStr = Util.getContextUrlString(this.config.url, this.config.busContext);
            urlStr = Util.convertToNonSecureUrl(urlStr.toString());
            URL url = new URL(urlStr.toString());

            // build the bus client
            RestClient busClient = new RestClient(url);

            // send the message to the bus
            jsonPayload = payloadBuilder.toPayload().toString();
            busClient.postTopicMessage("HawkularAvailData", jsonPayload, null);

            // looks like everything stored successfully
            // the metrics storage adapter already did this, so don't duplicate the stats here
            //diagnostics.getAvailRate().mark(payloadBuilder.getNumberDataPoints());

        } catch (Throwable t) {
            MsgLogger.LOG.errorFailedToStoreAvailData(t, jsonPayload);
            diagnostics.getStorageErrorRate().mark(1);
        }
    }

    @Override
    public void storeResourceType(ResourceType<?, ?, ?, ?> resourceType) {
        if (resourceType.isPersisted()) {
            return;
        }

        registerResourceType(resourceType);

        Collection<? extends MetricType> metricTypes = resourceType.getMetricTypes();
        for (MetricType metricType : metricTypes) {
            registerMetricType(metricType);
            relateResourceTypeWithMetricType(resourceType, metricType);
        }
        Collection<? extends AvailType> availTypes = resourceType.getAvailTypes();
        for (AvailType availType : availTypes) {
            registerMetricType(availType);
            relateResourceTypeWithMetricType(resourceType, availType);
        }

        MsgLogger.LOG.debugf("Stored resource type: %s", resourceType);
    }

    @Override
    public void storeResource(Resource<?, ?, ?, ?, ?> resource) {
        if (resource.isPersisted()) {
            return;
        }

        registerResource(resource);

        Collection<? extends MetricInstance<?, ?, ?>> metricInstances = resource.getMetrics();
        for (MetricInstance<?, ?, ?> metricInstance : metricInstances) {
            registerMetricInstance(metricInstance);
            relateResourceWithMetric(resource, metricInstance);
        }
        Collection<? extends AvailInstance<?, ?, ?>> availInstances = resource.getAvails();
        for (AvailInstance<?, ?, ?> availInstance : availInstances) {
            registerMetricInstance(availInstance);
            relateResourceWithMetric(resource, availInstance);
        }

        MsgLogger.LOG.debugf("Stored resource: %s", resource);
    }

    private String getInventoryId(NamedObject no) {
        String id;
        if (no.getID().equals(ID.NULL_ID)) {
            id = no.getName().getNameString();
        } else {
            id = no.getID().getIDString();
        }
        return id;
    }

    private void registerResource(Resource<?, ?, ?, ?, ?> resource) {
        if (resource.isPersisted()) {
            return;
        }
        if (resource.getParent() != null) {
            registerResource(resource.getParent());
        }

        try {
            // get the payload in JSON format
            org.hawkular.inventory.api.model.Resource.Blueprint rPojo;
            String resourceTypePath = "/" + getInventoryId(resource.getResourceType());
            rPojo = new org.hawkular.inventory.api.model.Resource.Blueprint(
                    getInventoryId(resource),
                    resourceTypePath,
                    resource.getProperties());
            final String jsonPayload = Util.toJson(rPojo);

            // build the REST URL
            StringBuilder url = Util.getContextUrlString(this.config.url, this.config.inventoryContext);
            url = Util.convertToNonSecureUrl(url.toString());
            url.append("test").append("/"); // environment
            url.append(getFeedId());
            url.append("/resources");
            if (resource.getParent() != null) {
                Stack<String> ancestors = new Stack<>();
                Resource it = resource;
                while ((it = it.getParent()) != null) {
                    ancestors.push(it.getID().getIDString());
                }
                while (!ancestors.empty()) {
                    url.append('/').append(ancestors.pop());
                }
            }

            // now send the REST request
            Request request = this.httpClientBuilder.buildJsonPostRequest(url.toString(), null, jsonPayload);
            Response response = this.httpClientBuilder.getHttpClient().newCall(request).execute();

            // HTTP status of 201 means success, 409 means it already exists; anything else is an error
            if (response.code() != 201 && response.code() != 409) {
                throw new Exception("status-code=[" + response.code() + "], reason=["
                        + response.message() + "], url=[" + request.urlString() + "]");
            }

            resource.setPersisted(true);

        } catch (Throwable t) {
            MsgLogger.LOG.errorFailedToStoreInventoryData(t);
            throw new RuntimeException("Cannot create resource: " + resource, t);
        }

        // now that the resource is registered, immediately register its configuration
        registerResourceConfiguration(resource);

        return;
    }

    private void registerResourceType(ResourceType<?, ?, ?, ?> resourceType) {
        if (resourceType.isPersisted()) {
            return;
        }

        try {
            // get the payload in JSON format
            org.hawkular.inventory.api.model.ResourceType.Blueprint rtPojo;
            rtPojo = new org.hawkular.inventory.api.model.ResourceType.Blueprint(
                    getInventoryId(resourceType),
                    resourceType.getProperties());
            final String jsonPayload = Util.toJson(rtPojo);

            // build the REST URL
            StringBuilder url = Util.getContextUrlString(this.config.url, this.config.inventoryContext);
            url = Util.convertToNonSecureUrl(url.toString());
            url.append("resourceTypes");

            // now send the REST request
            Request request = this.httpClientBuilder.buildJsonPostRequest(url.toString(), null, jsonPayload);
            Response response = this.httpClientBuilder.getHttpClient().newCall(request).execute();

            // HTTP status of 201 means success, 409 means it already exists; anything else is an error
            if (response.code() != 201 && response.code() != 409) {
                throw new Exception("status-code=[" + response.code() + "], reason=["
                        + response.message() + "], url=[" + request.urlString() + "]");
            }

            resourceType.setPersisted(true);

        } catch (Throwable t) {
            MsgLogger.LOG.errorFailedToStoreInventoryData(t);
            throw new RuntimeException("Cannot create resource type: " + resourceType, t);
        }
    }

    private void registerMetricInstance(MeasurementInstance<?, ?, ?> measurementInstance) {
        if (measurementInstance.isPersisted()) {
            return;
        }

        String metricId = getInventoryId(measurementInstance);
        String metricTypeId = getInventoryId(measurementInstance.getMeasurementType());
        // TODO: in next version of inventory feed will probably have it's own resource and metric types, so instead of
        // using the absolute path (/metricTypeId), the relative path (../metricTypeId) can be better here
        String metricTypePath = "/" + metricTypeId;
        Map<String, Object> metricProps = measurementInstance.getProperties();

        try {
            // get the payload in JSON format
            org.hawkular.inventory.api.model.Metric.Blueprint mPojo;
            mPojo = new org.hawkular.inventory.api.model.Metric.Blueprint(metricTypePath, metricId, metricProps);
            final String jsonPayload = Util.toJson(mPojo);

            // build the REST URL
            StringBuilder url = Util.getContextUrlString(this.config.url, this.config.inventoryContext);
            url = Util.convertToNonSecureUrl(url.toString());
            url.append("test").append("/"); // environment
            url.append(getFeedId());
            url.append("/metrics");

            // now send the REST request
            Request request = this.httpClientBuilder.buildJsonPostRequest(url.toString(), null, jsonPayload);
            Response response = this.httpClientBuilder.getHttpClient().newCall(request).execute();

            // HTTP status of 201 means success, 409 means it already exists; anything else is an error
            if (response.code() != 201 && response.code() != 409) {
                throw new Exception("status-code=[" + response.code() + "], reason=["
                        + response.message() + "], url=[" + request.urlString() + "]");
            }

            measurementInstance.setPersisted(true);

        } catch (Throwable t) {
            MsgLogger.LOG.errorFailedToStoreInventoryData(t);
            throw new RuntimeException("Cannot create metric type: " + metricTypeId, t);
        }
    }

    private void registerMetricType(MeasurementType measurementType) {
        if (measurementType.isPersisted()) {
            return;
        }

        String metricTypeId = getInventoryId(measurementType);
        Map<String, Object> metricTypeProps = measurementType.getProperties();

        try {
            MetricUnit mu = MetricUnit.NONE;
            MetricDataType metricDataType = MetricDataType.GAUGE;
            try {
                if (measurementType instanceof MetricType) {
                    mu = MetricUnit.valueOf(((MetricType) measurementType).getMetricUnits().name());

                    // we need to translate from metric API type to inventory API type
                    switch (((MetricType) measurementType).getMetricType()) {
                        case GAUGE:
                            metricDataType = MetricDataType.GAUGE;
                            break;
                        case COUNTER:
                            metricDataType = MetricDataType.COUNTER;
                            break;
                        default:
                            metricDataType = MetricDataType.GAUGE;
                            break;

                    }
                }
            } catch (Exception e) {
                // the unit isn't supported
            }

            // get the payload in JSON format
            org.hawkular.inventory.api.model.MetricType.Blueprint mtPojo;

            // TODO: correctly map the MetricDataType from the MeasurementType instance type (avail from AvailType etc.)
            mtPojo = new org.hawkular.inventory.api.model.MetricType.Blueprint(metricTypeId, mu, metricDataType,
                    metricTypeProps);
            final String jsonPayload = Util.toJson(mtPojo);

            // build the REST URL
            StringBuilder url = Util.getContextUrlString(this.config.url, this.config.inventoryContext);
            url = Util.convertToNonSecureUrl(url.toString());
            url.append("metricTypes");

            // now send the REST request
            Request request = this.httpClientBuilder.buildJsonPostRequest(url.toString(), null, jsonPayload);
            Response response = this.httpClientBuilder.getHttpClient().newCall(request).execute();

            // HTTP status of 201 means success, 409 means it already exists; anything else is an error
            if (response.code() != 201 && response.code() != 409) {
                throw new Exception("status-code=[" + response.code() + "], reason=["
                        + response.message() + "], url=[" + request.urlString() + "]");
            }

            measurementType.setPersisted(true);

        } catch (Throwable t) {
            MsgLogger.LOG.errorFailedToStoreInventoryData(t);
            throw new RuntimeException("Cannot create metric type: " + metricTypeId, t);
        }
    }

    private void relateResourceWithMetric(Resource<?, ?, ?, ?, ?> resource,
            MeasurementInstance<?, ?, ?> measInstance) {

        String metricId = getInventoryId(measInstance);

        try {
            // get the payload in JSON format
            Resource it = resource;
            int level = 1;
            while ((it = resource.getParent()) != null) {
                level++;
            }
            ArrayList<String> id = new ArrayList<>();
            id.add(String.join("", Collections.nCopies(level, "../")) + "m;" + metricId);
            final String jsonPayload = Util.toJson(id);

            // build the REST URL
            StringBuilder url = Util.getContextUrlString(this.config.url, this.config.inventoryContext);
            url = Util.convertToNonSecureUrl(url.toString());
            url.append("test").append("/"); // environment
            url.append(getFeedId());
            url.append("/resources").append(getResourcePath(resource)).append("/metrics");

            // now send the REST request
            Request request = this.httpClientBuilder.buildJsonPostRequest(url.toString(), null, jsonPayload);
            Response response = this.httpClientBuilder.getHttpClient().newCall(request).execute();

            // HTTP status of 204 means success, 409 means it already exists; anything else is an error
            if (response.code() != 204 && response.code() != 409) {
                throw new Exception("status-code=[" + response.code() + "], reason=["
                        + response.message() + "], url=[" + request.urlString() + "]");
            }
        } catch (Throwable t) {
            MsgLogger.LOG.errorFailedToStoreInventoryData(t);
            throw new RuntimeException("Cannot associate resource [" + getResourcePath(resource) + "] with metric [ " +
                    metricId + "]", t);
        }
    }

    private void relateResourceTypeWithMetricType(ResourceType<?, ?, ?, ?> resourceType, MeasurementType measType) {

        String resourceTypeId = getInventoryId(resourceType);
        String metricTypeId = getInventoryId(measType);

        try {
            // get the payload in JSON format
            ArrayList<String> id = new ArrayList<>();
            id.add("/mt;" + metricTypeId);
            final String jsonPayload = Util.toJson(id);

            // build the REST URL
            StringBuilder url = Util.getContextUrlString(this.config.url, this.config.inventoryContext);
            url = Util.convertToNonSecureUrl(url.toString());
            url.append("resourceTypes").append("/").append(Util.urlEncode(resourceTypeId)).append("/metricTypes");

            // now send the REST request
            Request request = this.httpClientBuilder.buildJsonPostRequest(url.toString(), null, jsonPayload);
            Response response = this.httpClientBuilder.getHttpClient().newCall(request).execute();

            // HTTP status of 204 means success, 409 means it already exists; anything else is an error
            if (response.code() != 204 && response.code() != 409) {
                throw new Exception("status-code=[" + response.code() + "], reason=["
                        + response.message() + "], url=[" + request.urlString() + "]");
            }
        } catch (Throwable t) {
            MsgLogger.LOG.errorFailedToStoreInventoryData(t);
            throw new RuntimeException("Cannot associate resource type with metric type: " + resourceTypeId + "/"
                    + metricTypeId, t);
        }
    }

    private void registerResourceConfiguration(Resource<?, ?, ?, ?, ?> resource) {

        try {
            Collection<? extends ResourceConfigurationPropertyInstance<?>> resConfigInstances =
                    resource.getResourceConfigurationProperties();

            if (resConfigInstances == null || resConfigInstances.isEmpty()) {
                return; // nothing to do
            }

            // get the payload in JSON format
            StructuredData.MapBuilder structDataBuilder = StructuredData.get().map();
            for (ResourceConfigurationPropertyInstance<?> resConfigInstance : resConfigInstances) {
                structDataBuilder.putString(resConfigInstance.getID().getIDString(), resConfigInstance.getValue());
            }

            org.hawkular.inventory.api.model.DataEntity.Blueprint dePojo;
            dePojo = new org.hawkular.inventory.api.model.DataEntity.Blueprint(
                    Resources.DataRole.configuration,
                    structDataBuilder.build(),
                    null);
            final String jsonPayload = Util.toJson(dePojo);

            // build the REST URL
            StringBuilder url = Util.getContextUrlString(this.config.url, this.config.inventoryContext);
            url = Util.convertToNonSecureUrl(url.toString());
            url.append("test").append("/"); // environment
            url.append(getFeedId());
            url.append("/resources");
            url.append(getResourcePath(resource));
            url.append("/data");

            // now send the REST request
            Request request = this.httpClientBuilder.buildJsonPostRequest(url.toString(), null, jsonPayload);
            Response response = this.httpClientBuilder.getHttpClient().newCall(request).execute();

            // HTTP status of 201 means success, 409 means it already exists; anything else is an error
            if (response.code() != 201 && response.code() != 409) {
                throw new Exception("status-code=[" + response.code() + "], reason=["
                        + response.message() + "], url=[" + request.urlString() + "]");
            }

        } catch (Throwable t) {
            MsgLogger.LOG.errorFailedToStoreInventoryData(t);
            throw new RuntimeException("Cannot register resource configuration for resource: " + resource, t);
        }
    }

    /**
     * For those inventory REST calls that need a resource path, this obtains that path.
     * It is just the hierarchy of IDs like "idGrandparent/idParent/resourceId".
     * The returned string will be properly encoded for use in a URL.
     *
     * @param resource resource whose path is to be returned
     * @return the resource path properly URL encoded. This will be prefixed with "/" always.
     */
    private String getResourcePath(Resource<?, ?, ?, ?, ?> resource) {
        String resourceIdPath = "/" + Util.urlEncode(resource.getID().getIDString());
        Resource<?, ?, ?, ?, ?> parent = resource.getParent();
        if (parent == null) {
            return resourceIdPath;
        } else {
            return getResourcePath(parent) + resourceIdPath;
        }
    }
}
