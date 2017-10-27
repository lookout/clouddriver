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
import com.amazonaws.services.ecs.model.DescribeTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.ListTaskDefinitionsRequest;
import com.amazonaws.services.ecs.model.ListTaskDefinitionsResult;
import com.amazonaws.services.ecs.model.TaskDefinition;
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
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASK_DEFINITIONS;

public class TaskDefinitionCachingAgent extends AbstractEcsOnDemandAgent<TaskDefinition> {
  static final Collection<AgentDataType> types = Collections.unmodifiableCollection(Arrays.asList(
    AUTHORITATIVE.forType(TASK_DEFINITIONS.toString())
  ));
  private final Logger log = LoggerFactory.getLogger(getClass());

  public TaskDefinitionCachingAgent(String accountName, String region, AmazonClientProvider amazonClientProvider, AWSCredentialsProvider awsCredentialsProvider, Registry registry) {
    super(accountName, region, amazonClientProvider, awsCredentialsProvider, registry);
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public String getAgentType() {
    return TaskDefinitionCachingAgent.class.getSimpleName();
  }

  @Override
  protected List<TaskDefinition> getItems(AmazonECS ecs, ProviderCache providerCache) {
    List<TaskDefinition> taskDefinitionList = new LinkedList<>();

    String nextToken = null;
    do {
      ListTaskDefinitionsRequest listTasksRequest = new ListTaskDefinitionsRequest();
      if (nextToken != null) {
        listTasksRequest.setNextToken(nextToken);
      }
      ListTaskDefinitionsResult listTaskDefinitionsResult = ecs.listTaskDefinitions(listTasksRequest);
      List<String> taskDefinitionArns = listTaskDefinitionsResult.getTaskDefinitionArns();
      if (taskDefinitionArns.size() == 0) {
        continue;
      }
      for (String taskDefintionArn : taskDefinitionArns) {
        TaskDefinition taskDefinition = ecs.describeTaskDefinition(new DescribeTaskDefinitionRequest().withTaskDefinition(taskDefintionArn)).getTaskDefinition();
        taskDefinitionList.add(taskDefinition);
      }
      nextToken = listTaskDefinitionsResult.getNextToken();
    } while (nextToken != null && nextToken.length() != 0);
    return taskDefinitionList;
  }

  @Override
  protected CacheResult buildCacheResult(List<TaskDefinition> taskDefinitions, ProviderCache providerCache) {
    Map<String, Collection<CacheData>> dataMap = generateFreshData(taskDefinitions);
    log.info("Caching " + dataMap.values().size() + " ECS task definitions  in " + getAgentType());


    Set<String> oldKeys = providerCache.getAll(TASK_DEFINITIONS.toString()).stream()
      .map(cache -> cache.getId()).collect(Collectors.toSet());

    Map<String, Collection<String>> evictions = computeEvictableData(dataMap.get(TASK_DEFINITIONS.toString()), oldKeys);
    log.info("Evicting " + evictions.size() + " ECS task definitions in " + getAgentType());

    return new DefaultCacheResult(dataMap, evictions);
  }

  @Override
  protected Map<String, Collection<CacheData>> generateFreshData(Collection<TaskDefinition> taskDefinitions) {
    Collection<CacheData> dataPoints = new LinkedList<>();

    for (TaskDefinition taskDefinition : taskDefinitions) {
      Map<String, Object> attributes = new HashMap<>();
      attributes.put("taskDefinitionArn", taskDefinition.getTaskDefinitionArn());
      attributes.put("containerDefinitions", taskDefinition.getContainerDefinitions());

      String key = Keys.getTaskDefinitionKey(accountName, region, taskDefinition.getTaskDefinitionArn());
      dataPoints.add(new DefaultCacheData(key, attributes, Collections.emptyMap()));
    }

    log.info("Caching " + dataPoints.size() + " task definitions in " + getAgentType());
    Map<String, Collection<CacheData>> dataMap = new HashMap<>();
    dataMap.put(TASK_DEFINITIONS.toString(), dataPoints);

    return dataMap;
  }

  @Override
  protected Map<String, Collection<String>> computeEvictableData(Collection<CacheData> newData, Collection<String> oldKeys) {
    Set<String> newKeys = newData.stream().map(newKey -> newKey.getId()).collect(Collectors.toSet());
    Set<String> evictedKeys = oldKeys.stream().filter(oldKey -> !newKeys.contains(oldKey)).collect(Collectors.toSet());

    Map<String, Collection<String>> evictionsByKey = new HashMap<>();
    evictionsByKey.put(TASK_DEFINITIONS.toString(), evictedKeys);
    return evictionsByKey;
  }
}
