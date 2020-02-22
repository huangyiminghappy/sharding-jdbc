/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.orchestration.internal.keygen;

import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.orchestration.center.api.DistributedLockManagement;
import org.apache.shardingsphere.orchestration.center.configuration.InstanceConfiguration;
import org.apache.shardingsphere.spi.NewInstanceServiceLoader;
import org.apache.shardingsphere.spi.TypeBasedSPIServiceLoader;

/**
 * Distributed lock management loader from SPI.
 */
@Slf4j
public final class DistributedLockManagementServiceLoader extends TypeBasedSPIServiceLoader<DistributedLockManagement> {
    
    static {
        NewInstanceServiceLoader.register(DistributedLockManagement.class);
    }
    
    public DistributedLockManagementServiceLoader() {
        super(DistributedLockManagement.class);
    }
    
    /**
     * Load distributed lock management from SPI.
     * 
     * @param config registry center configuration
     * @return distributed lock management
     */
    public DistributedLockManagement load(final InstanceConfiguration config) {
        Preconditions.checkNotNull(config, "Registry center configuration cannot be null.");
        DistributedLockManagement result = newService(config.getType(), config.getProperties());
        result.init(config);
        return result;
    }
}
