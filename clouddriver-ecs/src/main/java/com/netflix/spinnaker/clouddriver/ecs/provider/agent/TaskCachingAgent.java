package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.DescribeTasksRequest;
import com.amazonaws.services.ecs.model.ListTasksRequest;
import com.amazonaws.services.ecs.model.ListTasksResult;
import com.amazonaws.services.ecs.model.Task;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import groovy.lang.Closure;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.ON_DEMAND;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASKS;

public class TaskCachingAgent extends AbstractEcsOnDemandAgent<Task> {
  static final Collection<AgentDataType> types = Collections.unmodifiableCollection(Arrays.asList(
    AUTHORITATIVE.forType(TASKS.toString()),
    INFORMATIVE.forType(ECS_CLUSTERS.toString())
  ));
  private final Logger log = LoggerFactory.getLogger(getClass());

  public TaskCachingAgent(String accountName, String region, AmazonClientProvider amazonClientProvider, AWSCredentialsProvider awsCredentialsProvider, Registry registry) {
    super(accountName, region, amazonClientProvider, awsCredentialsProvider, registry);
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public String getAgentType() {
    return TaskCachingAgent.class.getSimpleName();
  }

  @Override
  protected List<Task> getItems(AmazonECS ecs, ProviderCache providerCache) {
    List<Task> taskList = new LinkedList<>();
    Set<String> clusters = getClusters(ecs, providerCache);

    for (String cluster : clusters) {
      String nextToken = null;
      do {
        ListTasksRequest listTasksRequest = new ListTasksRequest().withCluster(cluster);
        if (nextToken != null) {
          listTasksRequest.setNextToken(nextToken);
        }
        ListTasksResult listTasksResult = ecs.listTasks(listTasksRequest);
        List<String> taskArns = listTasksResult.getTaskArns();
        if (taskArns.size() == 0) {
          continue;
        }
        List<Task> tasks = ecs.describeTasks(new DescribeTasksRequest().withCluster(cluster).withTasks(taskArns)).getTasks();
        taskList.addAll(tasks);
        nextToken = listTasksResult.getNextToken();
      } while (nextToken != null && nextToken.length() != 0);
    }
    return taskList;
  }

  @Override
  protected CacheResult buildCacheResult(List<Task> tasks, ProviderCache providerCache) {
    Collection<CacheData> dataPoints = new LinkedList<>();
    Map<String, CacheData> clusterDataPoints = new HashMap<>();
    Set<String> evictingTaskKeys = providerCache.getAll(TASKS.toString()).stream()
      .map(cache -> cache.getId()).collect(Collectors.toSet());

    for (Task task : tasks) {
      String taskId = StringUtils.substringAfterLast(task.getTaskArn(), "/");
      Map<String, Object> attributes = new HashMap<>();
      attributes.put("taskId", taskId);
      attributes.put("taskArn", task.getTaskArn());
      attributes.put("clusterArn", task.getClusterArn());
      attributes.put("containerInstanceArn", task.getContainerInstanceArn());
      attributes.put("group", task.getGroup());
      //TODO: consider making containers a flat structure, if it cannot be deserialized.
      attributes.put("containers", task.getContainers());
      attributes.put("lastStatus", task.getLastStatus());
      attributes.put("desiredStatus", task.getDesiredStatus());
      attributes.put("startedAt", task.getStartedAt());

      String key = Keys.getTaskKey(accountName, region, taskId);
      dataPoints.add(new DefaultCacheData(key, attributes, Collections.emptyMap()));
      evictingTaskKeys.remove(key);

      String clusterName = StringUtils.substringAfterLast(task.getClusterArn(), "/");
      Map<String, Object> clusterAttributes = new HashMap<>();
      attributes.put("account", accountName);
      attributes.put("region", region);
      attributes.put("clusterName", clusterName);
      attributes.put("clusterArn", task.getClusterArn());
      key = Keys.getClusterKey(accountName, region, clusterName);
      clusterDataPoints.put(key, new DefaultCacheData(key, clusterAttributes, Collections.emptyMap()));
    }

    log.info("Caching " + dataPoints.size() + " tasks in " + getAgentType());
    Map<String, Collection<CacheData>> dataMap = new HashMap<>();
    dataMap.put(TASKS.toString(), dataPoints);

    log.info("Caching " + clusterDataPoints.size() + " ECS clusters in " + getAgentType());
    dataMap.put(ECS_CLUSTERS.toString(), clusterDataPoints.values());

    Map<String, Collection<String>> evictions = new HashMap<>();
    if (!evictingTaskKeys.isEmpty() && !tasks.isEmpty()) {
      evictions.put(TASKS.toString(), evictingTaskKeys);
    }
    log.info("Evicting " + evictions.size() + " tasks in " + getAgentType());

    return new DefaultCacheResult(dataMap, evictions);
  }

  @Override
  public Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    Collection<CacheData> allOnDemand = providerCache.getAll(ON_DEMAND.toString());
    List<Map> returnResults = new LinkedList<>();
    for (CacheData onDemand : allOnDemand) {
      Map<String, String> parsedKey = Keys.parse(onDemand.getId());
      if (parsedKey != null && parsedKey.get("type") != null &&
        (parsedKey.get("type").equals(SERVICES.toString()) || parsedKey.get("type").equals(TASKS.toString()))) {

        parsedKey.put("type", "serverGroup");
        parsedKey.put("serverGroup", parsedKey.get("serviceName"));

        HashMap<String, Object> result = new HashMap<>();
        result.put("id", onDemand.getId());
        result.put("details", parsedKey);

        result.put("cacheTime", onDemand.getAttributes().get("cacheTime"));
        result.put("cacheExpiry", onDemand.getAttributes().get("cacheExpiry"));
        result.put("processedCount", (onDemand.getAttributes().get("processedCount") != null ? onDemand.getAttributes().get("processedCount") : 1));
        result.put("processedTime", onDemand.getAttributes().get("processedTime") != null ? onDemand.getAttributes().get("processedTime") : new Date());

        returnResults.add(result);
      }
    }
    return returnResults;
  }

  @Override
  protected void storeOnDemand(ProviderCache providerCache, Map<String, ?> data) {
    metricsSupport.onDemandStore(new Closure<List<Task>>(this, this) {
      public void doCall() {
        String keyString = Keys.getServiceKey(accountName, region, (String) data.get("serverGroupName"));
        Map<String, Object> att = new HashMap<>();
        att.put("cacheTime", new Date());
        CacheData cacheData = new DefaultCacheData(keyString, att, Collections.emptyMap());
        providerCache.putCacheData(ON_DEMAND.toString(), cacheData);
      }
    });
  }
}
