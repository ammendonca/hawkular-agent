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
package org.hawkular.wildfly.module.installer;

import java.util.ArrayList;
import java.util.List;

import org.jboss.logging.Logger;

class RegisterExtension {

    private final Logger log = Logger.getLogger(this.getClass());

    public RegisterExtension() {
    }

    /**
     * registers extension to standalone.xml or domain.xml
     * @param options
     * @throws Exception
     */
    public void register(RegisterModuleConfiguration options) throws Exception {
        if (options.isDomain()) {
            registerToDomain(options);
        } else {
            registerToStandalone(options);
        }

        log.info("New serverConfig file written to [" + options.getTargetServerConfig().getAbsolutePath() + "]");
    }

    private void registerToStandalone(RegisterModuleConfiguration options) throws Exception {
        List<XmlEdit> inserts = new ArrayList<XmlEdit>();
        inserts.addAll(options.getXmlEdits());
        if (options.getModuleId() != null) {
            log.info("Register extension module=" + options.getModuleId());
            inserts.add(new XmlEdit("/server/extensions", "<extension module=\"" + options.getModuleId() + "\"/>"));
        }

        if (options.getSubsystem() != null) {
            inserts.add(new XmlEdit("/server/profile", options.getSubsystem()));
        }
        if (options.getSocketBindingGroups() != null && options.getSocketBinding() != null) {
            for (String group : options.getSocketBindingGroups()) {
                inserts.add(new XmlEdit("/server/socket-binding-group[@name='" + group + "']", options
                        .getSocketBinding()).withAttribute("name"));
            }
        }
        new XmlConfigBuilder(options.getSourceServerConfig(), options.getTargetServerConfig())
                .edits(inserts)
                .failNoMatch(options.isFailNoMatch()).build();
    }

    private void registerToDomain(RegisterModuleConfiguration options) throws Exception {
        List<XmlEdit> inserts = new ArrayList<XmlEdit>();
        inserts.addAll(options.getXmlEdits());
        if (options.getModuleId() != null) {
            log.info("Register extension module=" + options.getModuleId());
            inserts.add(new XmlEdit("/domain/extensions", "<extension module=\"" + options.getModuleId() + "\"/>"));
        }

        if (options.getSubsystem() != null) {
            for (String profile : options.getProfiles()) {
                inserts.add(new XmlEdit("/domain/profiles/profile[@name='"+profile+"']", options.getSubsystem()));
            }
        }
        if (options.getSocketBindingGroups() != null && options.getSocketBinding() != null) {
            for (String group : options.getSocketBindingGroups()) {
                inserts.add(new XmlEdit("/domain/socket-binding-group[@name='" + group + "']", options
                        .getSocketBinding()).withAttribute("name"));
            }
        }
        new XmlConfigBuilder(options.getSourceServerConfig(), options.getTargetServerConfig())
                .edits(inserts)
                .failNoMatch(options.isFailNoMatch()).build();
    }

}
