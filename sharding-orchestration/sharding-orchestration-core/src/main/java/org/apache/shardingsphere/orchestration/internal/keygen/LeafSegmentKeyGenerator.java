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
import com.google.common.base.Strings;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.shardingsphere.orchestration.center.api.DistributedLockManagement;
import org.apache.shardingsphere.orchestration.center.configuration.InstanceConfiguration;
import org.apache.shardingsphere.spi.keygen.ShardingKeyGenerator;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;

/**
 * Key generator implemented by leaf segment algorithms.
 */
public final class LeafSegmentKeyGenerator implements ShardingKeyGenerator {
    
    private static final String DEFAULT_NAMESPACE = "leaf_segment";
    
    private static final String DEFAULT_STEP = "10000";
    
    private static final String DEFAULT_INITIAL_VALUE = "1";
    
    private static final String DEFAULT_REGISTRY_CENTER = "zookeeper";
    
    private static final String SLANTING_BAR = "/";
    
    private static final String REGULAR_PATTERN = "^((?!/).)*$";
    
    private final ExecutorService incrementCacheIdExecutor;
    
    private final SynchronousQueue<Long> cacheIdQueue;
    
    private DistributedLockManagement distributedLockManagement;
    
    private long id;
    
    private long step;
    
    @Getter
    @Setter
    private Properties properties = new Properties();
    
    public LeafSegmentKeyGenerator() {
        incrementCacheIdExecutor = Executors.newSingleThreadExecutor();
        cacheIdQueue = new SynchronousQueue<>();
    }
    
    @Override
    public String getType() {
        return "LEAF_SEGMENT";
    }
    
    @Override
    public synchronized Comparable<?> generateKey() {
        String leafKey = getLeafKey();
        if (null == distributedLockManagement) {
            initLeafSegmentKeyGenerator(leafKey);
        } else {
            increaseIdWhenLeafKeyStoredInCenter(leafKey);
        }
        return id;
    }
    
    private void initLeafSegmentKeyGenerator(final String leafKey) {
        InstanceConfiguration leafConfiguration = getInstanceConfiguration();
        distributedLockManagement = new DistributedLockManagementServiceLoader().load(leafConfiguration);
        step = getStep();
        id = initializeId(leafKey);
        initializeLeafKeyInCenter(leafKey, id, step);
        initializeCacheIdAsynchronous(id, step);
    }
    
    private void increaseIdWhenLeafKeyStoredInCenter(final String leafKey) {
        if ((id % step) == 0) {
            id = tryTakeCacheId();
            incrementCacheIdAsynchronous(leafKey, step - (id % step));
        }
        ++id;
    }
    
    private long initializeId(final String leafKey) {
        String value = distributedLockManagement.get(leafKey);
        if (!Strings.isNullOrEmpty(value)) {
            return Long.parseLong(value) + 1;
        } else {
            return getInitialValue();
        }
    }
    
    private void initializeLeafKeyInCenter(final String leafKey, final long id, final long step) {
        distributedLockManagement.initLock(leafKey);
        while (!distributedLockManagement.tryLock()) {
            continue;
        }
        distributedLockManagement.persist(leafKey, String.valueOf(id + step - id % step));
        distributedLockManagement.tryRelease();
    }
    
    private void initializeCacheIdAsynchronous(final long id, final long step) {
        incrementCacheIdExecutor.execute(new Runnable() {

            @Override
            public void run() {
                tryPutCacheId(id + step - id % step);
            }
        });
    }
    
    private void incrementCacheIdAsynchronous(final String leafKey, final long step) {
        incrementCacheIdExecutor.execute(new Runnable() {

            @Override
            public void run() {
                long newId = incrementCacheId(leafKey, step);
                tryPutCacheId(newId);
            }
        });
    }
    
    private InstanceConfiguration getInstanceConfiguration() {
        InstanceConfiguration result = new InstanceConfiguration(getRegistryCenterType(), properties);
        result.setNamespace(DEFAULT_NAMESPACE);
        result.setServerLists(getServerList());
        return result;
    }
    
    @SneakyThrows
    private long incrementCacheId(final String leafKey, final long step) {
        while (!distributedLockManagement.tryLock()) {
            continue;
        }
        long result = updateCacheIdInCenter(leafKey, step);
        distributedLockManagement.tryRelease();
        return result;
    }
    
    private long updateCacheIdInCenter(final String leafKey, final long step) {
        String cacheIdInString = distributedLockManagement.get(leafKey);
        if (Strings.isNullOrEmpty(cacheIdInString)) {
            return Long.MIN_VALUE;
        }
        long cacheId = Long.parseLong(cacheIdInString);
        long result = cacheId + step;
        distributedLockManagement.persist(leafKey, String.valueOf(result));
        return result;
    }
    
    @SneakyThrows
    private void tryPutCacheId(final long id) {
        cacheIdQueue.put(id);
    }
    
    @SneakyThrows
    private long tryTakeCacheId() {
        return cacheIdQueue.take();
    }
    
    private long getStep() {
        long result = Long.parseLong(properties.getProperty("leaf.segment.step", DEFAULT_STEP));
        Preconditions.checkArgument(result > 0L && result < Long.MAX_VALUE);
        return result;
    }
    
    private long getInitialValue() {
        long result = Long.parseLong(properties.getProperty("leaf.segment.id.initial.value", DEFAULT_INITIAL_VALUE));
        Preconditions.checkArgument(result >= 0L && result < Long.MAX_VALUE);
        return result;
    }
    
    private String getLeafKey() {
        String leafKey = properties.getProperty("leaf.key");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(leafKey));
        Preconditions.checkArgument(leafKey.matches(REGULAR_PATTERN));
        return SLANTING_BAR + leafKey;
    }
    
    private String getServerList() {
        String result = properties.getProperty("server.list");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(result));
        return result;
    }
    
    private String getRegistryCenterType() {
        return properties.getProperty("registry.center.type", DEFAULT_REGISTRY_CENTER);
    }
}
