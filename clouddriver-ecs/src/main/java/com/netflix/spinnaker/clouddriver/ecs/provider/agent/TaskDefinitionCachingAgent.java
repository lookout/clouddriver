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

public class TaskDefinitionCachingAgent extends AbstractEcsCachingAgent<TaskDefinition> {
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
    Collection<CacheData> dataPoints = new LinkedList<>();
    Set<String> evictingTaskDefKeys = providerCache.getAll(TASK_DEFINITIONS.toString()).stream()
      .map(cache -> cache.getId()).collect(Collectors.toSet());

    for (TaskDefinition taskDefinition : taskDefinitions) {
      Map<String, Object> attributes = new HashMap<>();
      attributes.put("taskDefinitionArn", taskDefinition.getTaskDefinitionArn());
      //TODO: Remove taskArn attribute if it is not used anywhere.
      attributes.put("taskArn", taskDefinition.getContainerDefinitions());
      attributes.put("containerDefinitions", taskDefinition.getContainerDefinitions());

      String key = Keys.getTaskDefinitionKey(accountName, region, taskDefinition.getTaskDefinitionArn());
      dataPoints.add(new DefaultCacheData(key, attributes, Collections.emptyMap()));
      evictingTaskDefKeys.remove(key);
    }

    log.info("Caching " + dataPoints.size() + " task definitions in " + getAgentType());
    Map<String, Collection<CacheData>> dataMap = new HashMap<>();
    dataMap.put(TASK_DEFINITIONS.toString(), dataPoints);

    Map<String, Collection<String>> evictions = new HashMap<>();
    if (!evictingTaskDefKeys.isEmpty() && !taskDefinitions.isEmpty()) {
      evictions.put(TASK_DEFINITIONS.toString(), evictingTaskDefKeys);
    }
    log.info("Evicting " + evictions.size() + " task definitions in " + getAgentType());

    return new DefaultCacheResult(dataMap, evictions);
  }

}
