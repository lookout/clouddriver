package com.netflix.spinnaker.clouddriver.ecs.cache.client;

import com.amazonaws.services.ecs.model.LoadBalancer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;

public class ServiceCacheClient extends AbstractCacheClient<Service> {

  @Autowired
  public ServiceCacheClient(Cache cacheView) {
    super(cacheView, SERVICES.toString());
  }

  @Override
  protected Service convert(CacheData cacheData) {
    Service service = new Service();
    Map<String, Object> attributes = cacheData.getAttributes();

    service.setAccount((String) attributes.get("account"));
    service.setRegion((String) attributes.get("region"));
    service.setApplicationName((String) attributes.get("applicationName"));
    service.setServiceName((String) attributes.get("serviceName"));
    service.setServiceArn((String) attributes.get("serviceArn"));
    service.setClusterName((String) attributes.get("clusterName"));
    service.setClusterArn((String) attributes.get("clusterArn"));
    service.setRoleArn((String) attributes.get("roleArn"));
    service.setTaskDefinition((String) attributes.get("taskDefinition"));
    service.setDesiredCount((Integer) attributes.get("desiredCount"));
    service.setMaximumPercent((Integer) attributes.get("maximumPercent"));
    service.setMinimumHealthyPercent((Integer) attributes.get("minimumHealthyPercent"));

    if (attributes.containsKey("loadBalancers")) {
      ObjectMapper mapper = new ObjectMapper();
      List<Map<String, Object>> loadBalancers = (List<Map<String, Object>>) attributes.get("loadBalancers");
      List<LoadBalancer> deserializedLoadbalancers = new ArrayList<>(loadBalancers.size());

      for (Map<String, Object> serializedLoadbalancer : loadBalancers) {
        if(serializedLoadbalancer!=null) {
          deserializedLoadbalancers.add(mapper.convertValue(serializedLoadbalancer, LoadBalancer.class));
        }
      }

      service.setLoadBalancers(deserializedLoadbalancers);
    } else {
      service.setLoadBalancers(Collections.emptyList());
    }


    service.setCreatedAt((Long) attributes.get("createdAt"));

    return service;
  }
}
