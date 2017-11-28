package com.netflix.spinnaker.clouddriver.ecs.provider.view;

import com.netflix.spinnaker.clouddriver.aws.model.AmazonLoadBalancer;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsLoadbalancerCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsLoadBalancerCache;
import com.netflix.spinnaker.clouddriver.ecs.model.loadbalancer.EcsLoadBalancerDetail;
import com.netflix.spinnaker.clouddriver.ecs.model.loadbalancer.EcsLoadBalancerSummary;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class EcsLoadBalancerProvider implements LoadBalancerProvider<AmazonLoadBalancer> {

  private final EcsLoadbalancerCacheClient ecsLoadbalancerCacheClient;

  @Autowired
  public EcsLoadBalancerProvider(EcsLoadbalancerCacheClient ecsLoadbalancerCacheClient) {
    this.ecsLoadbalancerCacheClient = ecsLoadbalancerCacheClient;
  }

  @Override
  public String getCloudProvider() {
    return EcsCloudProvider.ID;
  }

  @Override
  public List<Item> list() {
    Map<String, EcsLoadBalancerSummary> map = new HashMap<>();
    List<EcsLoadBalancerCache> loadBalancers = ecsLoadbalancerCacheClient.findAll();

    for (EcsLoadBalancerCache lb : loadBalancers) {
      String name = lb.getLoadBalancerName();
      String account = lb.getAccount();
      String region = lb.getRegion();

      EcsLoadBalancerSummary summary = map.get(name);
      if (summary == null) {
        summary = new EcsLoadBalancerSummary().withName(name);
        map.put(name, summary);
      }

      EcsLoadBalancerDetail loadBalancer = new EcsLoadBalancerDetail();
      loadBalancer.setAccount(account);
      loadBalancer.setRegion(region);
      loadBalancer.setName(name);
      loadBalancer.setVpcId(lb.getVpcId());
      loadBalancer.setSecurityGroups(lb.getSecurityGroups());
      loadBalancer.setLoadBalancerType(lb.getLoadBalancerType());
      loadBalancer.setTargetGroups(lb.getTargetGroups());


      summary.getOrCreateAccount(account).getOrCreateRegion(region).getLoadBalancers().add(loadBalancer);
    }

    return new ArrayList<>(map.values());
  }


  @Override
  public Item get(String name) {
    return null;  //TODO - Implement this. Not even sure if it is used at all.
  }

  @Override
  public List<Details> byAccountAndRegionAndName(String account, String region, String name) {
    return null;  //TODO - Implement this.  This is used to show the details view of a load balancer which is not even implemented yet
  }

  @Override
  public Set<AmazonLoadBalancer> getApplicationLoadBalancers(String application) {
    /*Set<String> targetGroupNames = serviceCacheClient.getAll().stream()
      .filter(service -> service.getApplicationName().equals(application))
      .flatMap(service -> service.getLoadBalancers().stream()
        .map(loadBalancer -> StringUtils.substringBetween(loadBalancer.getTargetGroupArn(), "targetgroup/", "/"))
      )
      .collect(Collectors.toSet());

    Set<EcsLoadBalancerCache> ecsLoadBalancerCaches = ecsLoadbalancerCacheClient.findWithTargetGroups(targetGroupNames);
    //TODO: Make an EcsLoadBalancer model to use
    Set<AmazonLoadBalancer> amazonLoadBalancers = new HashSet<>();
    for(EcsLoadBalancerCache ecsLoadBalancerCache : ecsLoadBalancerCaches){
      AmazonLoadBalancer loadBalancer = new AmazonLoadBalancer();

      loadBalancer.setAccount(ecsLoadBalancerCache.getAccount());
      loadBalancer.setName(ecsLoadBalancerCache.getLoadBalancerName());
      loadBalancer.setRegion(ecsLoadBalancerCache.getRegion());
      //TODO: Populate with real values
      loadBalancer.setServerGroups(Collections.emptySet());
      loadBalancer.setTargetGroups(Collections.emptySet());
      loadBalancer.setVpcId(ecsLoadBalancerCache.getVpcId());

      amazonLoadBalancers.add(loadBalancer);
    }

    return amazonLoadBalancers;*/
    return null;  //TODO - Implement this.  This is used to show load balancers and reveals other buttons
  }
}
