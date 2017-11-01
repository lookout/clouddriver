package com.netflix.spinnaker.clouddriver.ecs.cache.client;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsCluster;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ECS_CLUSTERS;

public class EcsClusterCacheClient extends AbstractCacheClient<EcsCluster>{

  @Autowired
  public EcsClusterCacheClient(Cache cacheView) {
    super(cacheView, ECS_CLUSTERS.toString());
  }

  @Override
  protected EcsCluster convert(CacheData cacheData) {
    EcsCluster ecsCluster = new EcsCluster();
    Map<String, Object> attributes = cacheData.getAttributes();

    ecsCluster.setAccount((String) attributes.get("account"));
    ecsCluster.setRegion((String) attributes.get("region"));
    ecsCluster.setName((String) attributes.get("clusterName"));
    ecsCluster.setArn((String) attributes.get("clusterArn"));

    return ecsCluster;
  }
}
