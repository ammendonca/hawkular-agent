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
package org.hawkular.agent.test;

import org.hawkular.agent.monitor.api.HawkularWildFlyAgentContext;
import org.hawkular.cmdgw.ws.test.AbstractCommandITest;
import org.testng.annotations.Test;

/**
 * Tests accessing the {@link HawkularWildFlyAgentContext} API that is obtained via JNDI.
 * This assumes the example-jndi WAR is deployed in the test app server.
 */
public class HawkularWildFlyAgentContextITest extends AbstractCommandITest {

    @Test(dependsOnGroups = { "no-dependencies" }, groups = "agent-from-jndi")
    public void testAgentFromJNDI() throws Throwable {
        waitForAccountsAndInventory();

        // this should not exist yet
        assertResourceNotInInventory("/feeds/" + feedId + "/resourceTypes/MyAppResourceType/resources",
                (r -> r.getId().contains("ITest Resource ID")), 5, 5000);

        String createResource = getWithRetries(getExampleJndiWarCreateResourceUrl("ITest Resource ID"), 1, 1);

        // see that the new resource has been persisted to hawkular-inventory
        getResource("/feeds/" + feedId + "/resourceTypes/MyAppResourceType/resources",
                (r -> r.getId().contains("ITest Resource ID")));

        String metric = getWithRetries(getExampleJndiWarSendMetricUrl("ITest Metric Key", 123.0), 1, 1);
        String avail = getWithRetries(getExampleJndiWarSendAvailUrl("ITest Avail Key", "DOWN"), 1, 1);
        String removeResource = getWithRetries(getExampleJndiWarRemoveResourceUrl("ITest Resource ID"), 1, 1);

        // this should not exist anymore
        assertResourceNotInInventory("/feeds/" + feedId + "/resourceTypes/MyAppResourceType/resources",
                (r -> r.getId().contains("ITest Resource ID")), 5, 5000);

    }

    private String getExampleJndiWarCreateResourceUrl(String newResourceID) {
        return getExampleJndiWarServletUrl(String.format("newResourceID=%s", newResourceID));
    }

    private String getExampleJndiWarRemoveResourceUrl(String oldResourceID) {
        return getExampleJndiWarServletUrl(String.format("oldResourceID=%s", oldResourceID));
    }

    private String getExampleJndiWarSendMetricUrl(String metricKey, double metricValue) {
        return getExampleJndiWarServletUrl(String.format("metricKey=%s&metricValue=%f", metricKey, metricValue));
    }

    private String getExampleJndiWarSendAvailUrl(String availKey, String availValue) {
        return getExampleJndiWarServletUrl(String.format("availKey=%s&availValue=%s", availKey, availValue));
    }

    private String getExampleJndiWarServletUrl(String params) {
        return String.format("%s/MyAppServlet?%s", getExampleJndiWarUrl(), params);
    }

    private String getExampleJndiWarUrl() {
        return String.format("http://%s:%d/hawkular-wildfly-agent-example-jndi", host, httpPort);
    }
}
