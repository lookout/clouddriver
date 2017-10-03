package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.DescribeServicesRequest;
import com.amazonaws.services.ecs.model.ListServicesRequest;
import com.amazonaws.services.ecs.model.ListServicesResult;
import com.amazonaws.services.ecs.model.Service;
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
import org.apache.commons.lang3.StringUtils;
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
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;

public class ServiceCachingAgent implements CachingAgent, OnDemandAgent {
  static final Collection<AgentDataType> types = Collections.unmodifiableCollection(Arrays.asList(
    AUTHORITATIVE.forType(SERVICES.toString())
  ));
  private final Logger log = LoggerFactory.getLogger(getClass());
  private AmazonClientProvider amazonClientProvider;
  private AWSCredentialsProvider awsCredentialsProvider;
  private String region;
  private String accountName;
  private OnDemandMetricsSupport metricsSupport;

  public ServiceCachingAgent(String accountName, String region, AmazonClientProvider amazonClientProvider, AWSCredentialsProvider awsCredentialsProvider, Registry registry) {
    this.accountName = accountName;
    this.region = region;
    this.amazonClientProvider = amazonClientProvider;
    this.awsCredentialsProvider = awsCredentialsProvider;
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, EcsCloudProvider.ID + ":" + EcsCloudProvider.ID + ":${OnDemandAgent.OnDemandType.ServerGroup}");
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    AmazonECS ecs = amazonClientProvider.getAmazonEcs(accountName, awsCredentialsProvider, region);
    List<Service> services = getServices(ecs, providerCache);
    return buildCacheResult(services);
  }

  @Override
  public OnDemandResult handle(ProviderCache providerCache, Map<String, ?> data) {
    if (!data.get("account").equals(accountName) || !data.get("region").equals(region)) {
      return null;
    }

    AmazonECS ecs = amazonClientProvider.getAmazonEcs(accountName, awsCredentialsProvider, region);

    List<Service> services = metricsSupport.readData(new Closure<List<Service>>(this, this) {
      public List<Service> doCall() {
        return getServices(ecs, providerCache);
      }
    });

    CacheResult cacheResult = metricsSupport.transformData(new Closure<CacheResult>(this, this) {
      public CacheResult doCall() {
        return buildCacheResult(services);
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

  private List<Service> getServices(AmazonECS ecs, ProviderCache providerCache) {
    List<Service> serviceList = new LinkedList<>();
    Collection<CacheData> clusters = providerCache.getAll(ECS_CLUSTERS.toString());

    for (CacheData cluster : clusters) {
      String nextToken = null;
      do {
        ListServicesRequest listServicesRequest = new ListServicesRequest().withCluster((String) cluster.getAttributes().get("clusterName"));
        if (nextToken != null) {
          listServicesRequest.setNextToken(nextToken);
        }
        ListServicesResult listServicesResult = ecs.listServices(listServicesRequest);
        List<String> serviceArns = listServicesResult.getServiceArns();
        if (serviceArns.size() == 0) {
          continue;
        }

        List<Service> services = ecs.describeServices(new DescribeServicesRequest().withCluster((String) cluster.getAttributes().get("clusterName")).withServices(serviceArns)).getServices();
        serviceList.addAll(services);

        nextToken = listServicesResult.getNextToken();
      } while (nextToken != null && nextToken.length() != 0);
    }
    return serviceList;
  }

  private CacheResult buildCacheResult(List<Service> services) {
    Collection<CacheData> dataPoints = new LinkedList<>();
    for (Service service : services) {
      Map<String, Object> attributes = new HashMap<>();
      String applicationName = service.getServiceName().contains("-") ? StringUtils.substringBefore(service.getServiceName(), "-") : service.getServiceName();
      String clusterName = StringUtils.substringAfterLast(service.getClusterArn(), "/");

      attributes.put("applicationName", applicationName);
      attributes.put("serviceName", service.getServiceName());
      attributes.put("serviceArn", service.getServiceArn());
      attributes.put("clusterName", clusterName);
      attributes.put("clusterArn", service.getClusterArn());
      attributes.put("roleArn", service.getRoleArn());
      attributes.put("taskDefinition", service.getTaskDefinition());
      attributes.put("desiredCount", service.getDesiredCount());
      attributes.put("maximumPercent", service.getDeploymentConfiguration().getMaximumPercent());
      attributes.put("minimumHealthyPercent", service.getDeploymentConfiguration().getMinimumHealthyPercent());
      attributes.put("loadBalancers", service.getLoadBalancers());


      String key = Keys.getServiceKey(accountName, region, service.getServiceName());
      dataPoints.add(new DefaultCacheData(key, attributes, Collections.emptyMap()));
    }

    log.info("Caching " + dataPoints.size() + " services in " + getAgentType());
    Map<String, Collection<CacheData>> dataMap = new HashMap<>();
    dataMap.put(SERVICES.toString(), dataPoints);
    return new DefaultCacheResult(dataMap);
  }

  @Override
  public String getAgentType() {
    return ServiceCachingAgent.class.getSimpleName();
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
    return types;
  }
}
