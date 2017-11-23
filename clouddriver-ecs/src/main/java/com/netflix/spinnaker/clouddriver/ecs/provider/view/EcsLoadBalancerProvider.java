package com.netflix.spinnaker.clouddriver.ecs.provider.view;

import com.amazonaws.services.ecs.model.LoadBalancer;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.clouddriver.aws.model.AmazonLoadBalancer;
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonLoadBalancerProvider.AmazonLoadBalancerDetail;
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonLoadBalancerProvider.AmazonLoadBalancerSummary;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.model.loadbalancer.EcsLoadBalancer;
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class EcsLoadBalancerProvider implements LoadBalancerProvider<AmazonLoadBalancer> {

  private final ServiceCacheClient serviceCacheClient;
  private final EcsLoadbalancerpCacheClient ecsLoadbalancerpCacheClient;


  @Autowired
  public EcsLoadBalancerProvider(Cache cacheView,
                                 EcsLoadbalancerpCacheClient ecsLoadbalancerpCacheClient) {
    this.ecsLoadbalancerpCacheClient = ecsLoadbalancerpCacheClient;
    this.serviceCacheClient = new ServiceCacheClient(cacheView);
  }

  @Override
  public String getCloudProvider() {
    return EcsCloudProvider.ID;
  }

  @Override
  public List<Item> list() {
    Map<String, AmazonLoadBalancerSummary> map = new HashMap<>();
    List<EcsLoadBalancer> loadBalancers = ecsLoadbalancerpCacheClient.findAll();

    for (EcsLoadBalancer lb : loadBalancers) {
      String name = lb.getLoadBalancerName();
      String account = lb.getAccount();
      String region = lb.getRegion();

      AmazonLoadBalancerSummary summary = map.get(name);
      if (summary == null) {
        summary = new AmazonLoadBalancerSummary();
        summary.setName(name);
        map.put(name, summary);
      }

      AmazonLoadBalancerDetail loadBalancer = new AmazonLoadBalancerDetail();
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
    Set<String> targetGroupNames = serviceCacheClient.getAll().stream()
      .filter(service -> service.getApplicationName().equals(application))
      .flatMap(service -> service.getLoadBalancers().stream()
        .map(loadBalancer -> StringUtils.substringBetween(loadBalancer.getTargetGroupArn(), "targetgroup/", "/"))
      )
      .collect(Collectors.toSet());

    Set<EcsLoadBalancer> ecsLoadBalancers = ecsLoadbalancerpCacheClient.findWithTargetGroups(targetGroupNames);
    Set<AmazonLoadBalancer> amazonLoadBalancers = new HashSet<>();
    for(EcsLoadBalancer ecsLoadBalancer:ecsLoadBalancers){
      AmazonLoadBalancer loadBalancer = new AmazonLoadBalancer();

      loadBalancer.setAccount(ecsLoadBalancer.getAccount());
      loadBalancer.setName(ecsLoadBalancer.getLoadBalancerName());
      loadBalancer.setRegion(ecsLoadBalancer.getRegion());
      loadBalancer.setServerGroups(Collections.emptySet()); //TODO: Populate with real values
      loadBalancer.setTargetGroups(Collections.emptySet());
      loadBalancer.setVpcId(ecsLoadBalancer.getVpcId());

      amazonLoadBalancers.add(loadBalancer);
    }

    return amazonLoadBalancers;  //TODO - Implement this.  This is used to show load balancers and reveals other buttons
  }
}
