/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.Task;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import groovy.lang.Closure;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.ON_DEMAND;

abstract class AbstractEcsOnDemandAgent<T> extends AbstractEcsCachingAgent<T> implements OnDemandAgent {
  final OnDemandMetricsSupport metricsSupport;
  private ObjectMapper objectMapper;

  AbstractEcsOnDemandAgent(String accountName, String region, AmazonClientProvider amazonClientProvider, AWSCredentialsProvider awsCredentialsProvider, Registry registry, ObjectMapper objectMapper) {
    super(accountName, region, amazonClientProvider, awsCredentialsProvider);
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, EcsCloudProvider.ID + ":" + EcsCloudProvider.ID + ":${OnDemandAgent.OnDemandType.ServerGroup}");
    this.objectMapper = objectMapper;
  }

  @Override
  public OnDemandMetricsSupport getMetricsSupport() {
    return metricsSupport;
  }

  @Override
  public Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    return new LinkedList<>();
  }

  @Override
  public String getOnDemandAgentType() {
    return getAgentType() + "-OnDemand";
  }

  protected abstract String getCachingKey(String id);

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return type.equals(OnDemandType.ServerGroup) && cloudProvider.equals(EcsCloudProvider.ID);
  }

  @Override
  public OnDemandResult handle(ProviderCache providerCache, Map<String, ?> data) {
    if (!data.get("account").equals(accountName) || !data.get("region").equals(region)) {
      return null;
    }

    AmazonECS ecs = amazonClientProvider.getAmazonEcs(accountName, awsCredentialsProvider, region);

    List<T> items = metricsSupport.readData(new Closure<List<T>>(this, this) {
      public List<T> doCall() {
        return getItems(ecs, providerCache);
      }
    });

    CacheResult cacheResult = metricsSupport.transformData(new Closure<CacheResult>(this, this) {
      public CacheResult doCall() {
        return buildCacheResult(getAuthoritativeKeyName(), items, providerCache);
      }
    });

    metricsSupport.onDemandStore(new Closure<List<Task>>(this, this) {
      public void doCall() {
        //Cache data will be stored in the ON_DEMAND, but using a key of the caching agent type.
        String keyString = getCachingKey((String) data.get("serverGroupName"));
          //Keys.getServiceKey(accountName, region, (String) data.get("serverGroupName"));
        Map<String, Object> att = new HashMap<>();
        att.put("cacheTime", new Date());
        try {
          att.put("cacheResults", objectMapper.writeValueAsString(cacheResult));
        } catch (JsonProcessingException e) {
          att.put("cacheResults", null);
        }
        att.put("processedCount", 0);
        att.put("processedTime", 0);
        CacheData cacheData = new DefaultCacheData(keyString, 600, att, Collections.emptyMap());
        providerCache.putCacheData(ON_DEMAND.toString(), cacheData);
      }
    });

    return new OnDemandResult(getAgentType(), cacheResult, cacheResult.getEvictions());
  }
}
