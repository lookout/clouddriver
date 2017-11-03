package com.netflix.spinnaker.clouddriver.ecs.cache.client;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.TaskHealth;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH;

public class TaskHealthCacheClient extends AbstractCacheClient<TaskHealth> {

  @Autowired
  public TaskHealthCacheClient(Cache cacheView) {
    super(cacheView, HEALTH.toString());
  }

  @Override
  protected TaskHealth convert(CacheData cacheData) {
    TaskHealth taskHealth = new TaskHealth();

    Map<String, Object> attributes = cacheData.getAttributes();
    taskHealth.setState((String) attributes.get("state"));
    taskHealth.setType((String) attributes.get("type"));
    taskHealth.setServiceName((String) attributes.get("service"));
    taskHealth.setTaskArn((String) attributes.get("taskArn"));
    taskHealth.setInstanceId((String) attributes.get("instanceId"));
    taskHealth.setTaskId((String) attributes.get("taskId"));

    return taskHealth;
  }
}
