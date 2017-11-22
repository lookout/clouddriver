package com.netflix.spinnaker.clouddriver.ecs.provider.view;

import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.model.loadbalancer.EcsLoadBalancer;
import com.netflix.spinnaker.clouddriver.ecs.model.loadbalancer.EcsTargetGroup;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class EcsLoadBalancerProvider implements LoadBalancerProvider<EcsLoadBalancer> {

  private final EcsTargetGroupCacheClient ecsTargetGroupCacheClient;


  @Autowired
  public EcsLoadBalancerProvider(EcsTargetGroupCacheClient ecsTargetGroupCacheClient) {
    this.ecsTargetGroupCacheClient = ecsTargetGroupCacheClient;
  }
  @Override
  public String getCloudProvider() {
    return EcsCloudProvider.ID;
  }

  @Override
  public List<Item> list() {
    List<EcsTargetGroup> ecsTargetGroups = ecsTargetGroupCacheClient.findAll();
    // TODO - convert ecsTargetGroups to a list of Items, perhaps by using a new class dedicated to doing that conversion.  It could be called EcsLoadBalancerProviderService

    return null;  // TODO - Danil, this is the only method that needs to be implemented for the target group story
  }

  /**
  // TODO - this is great inspiration for a solution.  Although it is groovy-specific, it's a great starting point
  static class AmazonLoadBalancerSummary implements LoadBalancerProvider.Item {
    private Map<String, AmazonLoadBalancerAccount> mappedAccounts = [:]
    String name

    AmazonLoadBalancerAccount getOrCreateAccount(String name) {
      if (!mappedAccounts.containsKey(name)) {
        mappedAccounts.put(name, new AmazonLoadBalancerAccount(name: name))
      }
      mappedAccounts[name]
    }

    @JsonProperty("accounts")
    List<AmazonLoadBalancerAccount> getByAccounts() {
      mappedAccounts.values() as List
    }
  }
  */


  @Override
  public Item get(String name) {
    return null;  //TODO - Implement this. Not even sure if it is used at all.
  }

  @Override
  public List<Details> byAccountAndRegionAndName(String account, String region, String name) {
    return null;  //TODO - Implement this.  This is used to show the details view of a load balancer which is not even implemented yet
  }

  @Override
  public Set<EcsLoadBalancer> getApplicationLoadBalancers(String application) {
    return null;  //TODO - Implement this.  This is used to show load balancers and reveals other buttons
  }
}
