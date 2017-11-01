package com.netflix.spinnaker.clouddriver.ecs.cache.client;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.ContainerInstance;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.CONTAINER_INSTANCES;

public class ContainerInstanceCacheClient extends AbstractCacheClient<ContainerInstance> {

  @Autowired
  public ContainerInstanceCacheClient(Cache cacheView) {
    super(cacheView, CONTAINER_INSTANCES.toString());
  }

  @Override
  protected ContainerInstance convert(CacheData cacheData) {
    ContainerInstance containerInstance = new ContainerInstance();
    Map<String, Object> attributes = cacheData.getAttributes();
    containerInstance.setArn((String) attributes.get("containerInstanceArn"));
    containerInstance.setEc2InstanceId((String) attributes.get("ec2InstanceId"));

    return containerInstance;
  }
}
