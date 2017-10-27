/*
 * * Copyright 2017 Lookout, Inc.
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
import com.amazonaws.services.ecs.model.ContainerInstance;
import com.amazonaws.services.ecs.model.DescribeContainerInstancesRequest;
import com.amazonaws.services.ecs.model.ListContainerInstancesRequest;
import com.amazonaws.services.ecs.model.ListContainerInstancesResult;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.CONTAINER_INSTANCES;

public class ContainerInstanceCachingAgent extends AbstractEcsOnDemandAgent<ContainerInstance> {
  static final Collection<AgentDataType> types = Collections.unmodifiableCollection(Arrays.asList(
    AUTHORITATIVE.forType(CONTAINER_INSTANCES.toString())
  ));
  private final Logger log = LoggerFactory.getLogger(getClass());

  public ContainerInstanceCachingAgent(String accountName, String region, AmazonClientProvider amazonClientProvider, AWSCredentialsProvider awsCredentialsProvider, Registry registry) {
    super(accountName, region, amazonClientProvider, awsCredentialsProvider, registry);
  }

  @Override
  public String getAgentType() {
    return ContainerInstanceCachingAgent.class.getSimpleName();
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  protected List<ContainerInstance> getItems(AmazonECS ecs, ProviderCache providerCache) {
    List<ContainerInstance> containerInstanceList = new LinkedList<>();
    Set<String> clusters = getClusters(ecs, providerCache);

    for (String cluster : clusters) {
      String nextToken = null;
      do {
        ListContainerInstancesRequest listContainerInstancesRequest = new ListContainerInstancesRequest().withCluster(cluster);
        if (nextToken != null) {
          listContainerInstancesRequest.setNextToken(nextToken);
        }

        ListContainerInstancesResult listContainerInstancesResult = ecs.listContainerInstances(listContainerInstancesRequest);
        List<String> containerInstanceArns = listContainerInstancesResult.getContainerInstanceArns();
        if (containerInstanceArns.size() == 0) {
          continue;
        }

        List<ContainerInstance> containerInstances = ecs.describeContainerInstances(new DescribeContainerInstancesRequest()
          .withCluster(cluster).withContainerInstances(containerInstanceArns)).getContainerInstances();
        containerInstanceList.addAll(containerInstances);

        nextToken = listContainerInstancesResult.getNextToken();
      } while (nextToken != null && nextToken.length() != 0);
    }
    return containerInstanceList;
  }

  @Override
  protected CacheResult buildCacheResult(List<ContainerInstance> containerInstances, ProviderCache providerCache) {
    Map<String, Collection<CacheData>> dataMap = generateFreshData(containerInstances);
    log.info("Caching " + dataMap.values().size() + " ECS container instances in " + getAgentType());


    Set<String> oldKeys = providerCache.getAll(CONTAINER_INSTANCES.toString()).stream()
      .map(cache -> cache.getId()).collect(Collectors.toSet());

    Map<String, Collection<String>> evictions = computeEvictableData(dataMap.get(CONTAINER_INSTANCES.toString()), oldKeys);
    log.info("Evicting " + evictions.size() + " ECS container instances in " + getAgentType());

    return new DefaultCacheResult(dataMap, evictions);
  }

  @Override
  protected Map<String, Collection<CacheData>> generateFreshData(Collection<ContainerInstance> containerInstances) {
    Collection<CacheData> dataPoints = new LinkedList<>();

    for (ContainerInstance containerInstance : containerInstances) {
      Map<String, Object> attributes = new HashMap<>();
      attributes.put("containerInstanceArn", containerInstance.getContainerInstanceArn());
      attributes.put("ec2InstanceId", containerInstance.getEc2InstanceId());

      String key = Keys.getContainerInstanceKey(accountName, region, containerInstance.getContainerInstanceArn());
      dataPoints.add(new DefaultCacheData(key, attributes, Collections.emptyMap()));
    }

    log.info("Caching " + dataPoints.size() + " container instances in " + getAgentType());
    Map<String, Collection<CacheData>> dataMap = new HashMap<>();
    dataMap.put(CONTAINER_INSTANCES.toString(), dataPoints);

    return dataMap;
  }

  @Override
  protected Map<String, Collection<String>> computeEvictableData(Collection<CacheData> newData, Collection<String> oldKeys) {
    Set<String> newKeys = newData.stream().map(newKey -> newKey.getId()).collect(Collectors.toSet());
    Set<String> evictedKeys = oldKeys.stream().filter(oldKey -> !newKeys.contains(oldKey)).collect(Collectors.toSet());

    Map<String, Collection<String>> evictionsByKey = new HashMap<>();
    evictionsByKey.put(CONTAINER_INSTANCES.toString(), evictedKeys);
    return evictionsByKey;
  }
}
