/*
 * Copyright (c) 2020 Aaron Coburn and individual contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.trellisldp.helidon.app;

import static java.util.Collections.unmodifiableMap;
import static org.trellisldp.app.AppUtils.printBanner;

import java.util.LinkedHashMap;
import java.util.Map;

import org.trellisldp.common.ServiceBundler;
import org.trellisldp.helidon.AbstractTrellisApplication;
import org.trellisldp.helidon.HealthCheck;
import org.trellisldp.helidon.Lifecycle;

/**
 * A deployable Trellis application.
 */
public class TrellisApplication extends AbstractTrellisApplication<AppConfiguration> {

    private final Map<String, HealthCheck> healthChecks = new LinkedHashMap<>();

    private ServiceBundler serviceBundler;

    /**
     * The main entry point.
     *
     * @param args the argument list
     * @throws Exception if something goes horribly awry
     */
    public static void main(final String[] args) throws Exception {
        new TrellisApplication().run(args);
    }

    @Override
    protected ServiceBundler getServiceBundler() {
        return serviceBundler;
    }

    @Override
    protected Class<AppConfiguration> getConfigurationClass() {
        return AppConfiguration.class;
    }

    @Override
    protected Map<String, HealthCheck> getHealthChecks() {
        return unmodifiableMap(healthChecks);
    }

    @Override
    protected void initialize(final AppConfiguration config, final Lifecycle lifecycle) {
        super.initialize(config, lifecycle);
        healthChecks.put("deadlocks", TrellisServiceBundler::deadlockCheck);
        this.serviceBundler = new TrellisServiceBundler(config, lifecycle, healthChecks::put);
        healthChecks.put("trellis", HealthCheck.Result::healthy);
        printBanner("Trellis Application", "org/trellisldp/app/banner.txt");
    }
}
