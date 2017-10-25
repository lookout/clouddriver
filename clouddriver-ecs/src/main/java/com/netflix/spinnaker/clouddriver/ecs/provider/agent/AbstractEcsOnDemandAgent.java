package com.netflix.spinnaker.clouddriver.ecs.provider.agent;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.ListClustersRequest;
import com.amazonaws.services.ecs.model.ListClustersResult;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent;
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.provider.EcsProvider;
import groovy.lang.Closure;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;

public abstract class AbstractEcsOnDemandAgent<T> extends  AbstractEcsCachingAgent<T> implements OnDemandAgent {
  protected OnDemandMetricsSupport metricsSupport;

  public AbstractEcsOnDemandAgent(String accountName, String region, AmazonClientProvider amazonClientProvider, AWSCredentialsProvider awsCredentialsProvider, Registry registry) {
    super(accountName, region, amazonClientProvider, awsCredentialsProvider);
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, EcsCloudProvider.ID + ":" + EcsCloudProvider.ID + ":${OnDemandAgent.OnDemandType.ServerGroup}");
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
    return getAgentType();
  }

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

    storeOnDemand(providerCache, data);

    CacheResult cacheResult = metricsSupport.transformData(new Closure<CacheResult>(this, this) {
      public CacheResult doCall() {
        return buildCacheResult(items, providerCache);
      }
    });


    Collection<String> typeStrings = new LinkedList<>();
    for (AgentDataType agentDataType : getProvidedDataTypes()) {
      typeStrings.add(agentDataType.toString());
    }

    OnDemandResult result = new OnDemandResult();
    result.setAuthoritativeTypes(typeStrings);
    result.setCacheResult(cacheResult);
    result.setSourceAgentType(getAgentType());

    return result;
  }

  protected void storeOnDemand(ProviderCache providerCache, Map<String, ?> data) {
    // TODO: Overwrite if needed.
  }
}
