package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.ListClustersRequest;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.provider.EcsProvider;
import org.apache.commons.lang.StringUtils;
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
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;

public class EcsClusterCachingAgent extends AbstractEcsCachingAgent<String> {
  static final Collection<AgentDataType> types = Collections.unmodifiableCollection(Arrays.asList(
    AUTHORITATIVE.forType(ECS_CLUSTERS.toString())
  ));
  private final Logger log = LoggerFactory.getLogger(getClass());

  public EcsClusterCachingAgent(String accountName, String region, AmazonClientProvider amazonClientProvider, AWSCredentialsProvider awsCredentialsProvider) {
    super(accountName, region, amazonClientProvider, awsCredentialsProvider);
  }

  @Override
  public String getAgentType() {
    return EcsClusterCachingAgent.class.getSimpleName();
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  protected List<String> getItems(AmazonECS ecs, ProviderCache providerCache) {
    List<String> allClusterArns = new LinkedList<>();
    String nextToken = null;
    do {
      ListClustersRequest listClustersRequest = new ListClustersRequest();
      if (nextToken != null) {
        listClustersRequest.setNextToken(nextToken);
      }

      ListClustersResult listClustersResult = ecs.listClusters(listClustersRequest);
      allClusterArns.addAll(listClustersResult.getClusterArns());

      nextToken = listClustersResult.getNextToken();
    } while (nextToken != null && nextToken.length() != 0);

    return allClusterArns;
  }

  @Override
  protected CacheResult buildCacheResult(List<String> clusterArns, ProviderCache providerCache) {
    Collection<CacheData> dataPoints = new LinkedList<>();
    Set<String> evictingClusterKeys = providerCache.getAll(ECS_CLUSTERS.toString()).stream()
      .map(cache -> cache.getId()).collect(Collectors.toSet());

    for (String clusterArn : clusterArns) {
      String clusterName = StringUtils.substringAfterLast(clusterArn, "/");

      Map<String, Object> attributes = new HashMap<>();
      attributes.put("account", accountName);
      attributes.put("region", region);
      attributes.put("clusterName", clusterName);
      attributes.put("clusterArn", clusterArn);

      String key = Keys.getClusterKey(accountName, region, clusterName);
      dataPoints.add(new DefaultCacheData(key, attributes, Collections.emptyMap()));
      evictingClusterKeys.remove(key);
    }

    log.info("Caching " + dataPoints.size() + " ECS clusters in " + getAgentType());
    Map<String, Collection<CacheData>> dataMap = new HashMap<>();
    dataMap.put(ECS_CLUSTERS.toString(), dataPoints);

    Map<String, Collection<String>> evictions = new HashMap<>();
    if (!evictingClusterKeys.isEmpty() && !evictingClusterKeys.isEmpty()) {
      evictions.put(ECS_CLUSTERS.toString(), evictingClusterKeys);
    }
    log.info("Evicting " + evictions.size() + " ECS clusters in " + getAgentType());

    return new DefaultCacheResult(dataMap, evictions);
  }
}
