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
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.agent.DefaultCacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.provider.EcsProvider;
import groovy.lang.Closure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.CONTAINER_INSTANCES;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;

public class ContainerInstanceCachingAgent implements CachingAgent, OnDemandAgent {
  static final Collection<AgentDataType> types = Collections.unmodifiableCollection(Arrays.asList(
    AUTHORITATIVE.forType(CONTAINER_INSTANCES.toString())
  ));
  private final Logger log = LoggerFactory.getLogger(getClass());
  private AmazonClientProvider amazonClientProvider;
  private AWSCredentialsProvider awsCredentialsProvider;
  private String region;
  private String accountName;
  private OnDemandMetricsSupport metricsSupport;

  public ContainerInstanceCachingAgent(String accountName, String region, AmazonClientProvider amazonClientProvider, AWSCredentialsProvider awsCredentialsProvider, Registry registry) {
    this.accountName = accountName;
    this.region = region;
    this.amazonClientProvider = amazonClientProvider;
    this.awsCredentialsProvider = awsCredentialsProvider;
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, EcsCloudProvider.ID + ":" + EcsCloudProvider.ID + ":${OnDemandAgent.OnDemandType.ServerGroup}");
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    AmazonECS ecs = amazonClientProvider.getAmazonEcs(accountName, awsCredentialsProvider, region);
    List<ContainerInstance> containerInstances = getContainerInstances(ecs, providerCache);
    return buildCacheResult(containerInstances);
  }

  @Override
  public OnDemandResult handle(ProviderCache providerCache, Map<String, ?> data) {
    if (!data.get("account").equals(accountName) || !data.get("region").equals(region)) {
      return null;
    }

    AmazonECS ecs = amazonClientProvider.getAmazonEcs(accountName, awsCredentialsProvider, region);

    List<ContainerInstance> containerInstances = metricsSupport.readData(new Closure<List<ContainerInstance>>(this, this) {
      public List<ContainerInstance> doCall() {
        return getContainerInstances(ecs, providerCache);
      }
    });

    CacheResult cacheResult = metricsSupport.transformData(new Closure<CacheResult>(this, this) {
      public CacheResult doCall() {
        return buildCacheResult(containerInstances);
      }
    });


    Collection<String> typeStrings = new LinkedList<>();
    for (AgentDataType agentDataType : this.types) {
      typeStrings.add(agentDataType.toString());
    }

    OnDemandResult result = new OnDemandResult();
    result.setAuthoritativeTypes(typeStrings);
    result.setCacheResult(cacheResult);
    result.setSourceAgentType(getAgentType());

    return result;
  }

  private List<ContainerInstance> getContainerInstances(AmazonECS ecs, ProviderCache providerCache) {
    List<ContainerInstance> containerInstanceList = new LinkedList<>();
    Collection<CacheData> clusters = providerCache.getAll(ECS_CLUSTERS.toString());

    for (CacheData cluster : clusters) {
      String nextToken = null;
      do {
        ListContainerInstancesRequest listContainerInstancesRequest = new ListContainerInstancesRequest().withCluster((String) cluster.getAttributes().get("clusterName"));
        if (nextToken != null) {
          listContainerInstancesRequest.setNextToken(nextToken);
        }

        ListContainerInstancesResult listContainerInstancesResult = ecs.listContainerInstances(listContainerInstancesRequest);
        List<String> containerInstanceArns = listContainerInstancesResult.getContainerInstanceArns();
        if (containerInstanceArns.size() == 0) {
          continue;
        }

        List<ContainerInstance> containerInstances = ecs.describeContainerInstances(new DescribeContainerInstancesRequest()
          .withCluster((String) cluster.getAttributes().get("clusterName")).withContainerInstances(containerInstanceArns)).getContainerInstances();
        containerInstanceList.addAll(containerInstances);

        nextToken = listContainerInstancesResult.getNextToken();
      } while (nextToken != null && nextToken.length() != 0);
    }
    return containerInstanceList;
  }

  private CacheResult buildCacheResult(List<ContainerInstance> containerInstances) {
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
    return new DefaultCacheResult(dataMap);
  }

  @Override
  public String getAgentType() {
    return ContainerInstanceCachingAgent.class.getSimpleName();
  }

  @Override
  public String getProviderName() {
    return EcsProvider.NAME;
  }

  @Override
  public String getOnDemandAgentType() {
    return getAgentType();
  }

  @Override
  public OnDemandMetricsSupport getMetricsSupport() {
    return metricsSupport;
  }

  @Override
  public Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    return Collections.emptyList();
  }

  @Override
  public boolean handles(OnDemandType type, String cloudProvider) {
    return type.equals(OnDemandType.ServerGroup) && cloudProvider.equals(EcsCloudProvider.ID);
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return Collections.emptyList();
  }
}
