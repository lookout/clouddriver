package com.netflix.spinnaker.clouddriver.ecs.cache.client;

import com.amazonaws.services.ecs.model.Container;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Task;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASKS;

public class TaskCacheClient extends AbstractCacheClient<Task> {

  @Autowired
  public TaskCacheClient(Cache cacheView) {
    super(cacheView, TASKS.toString());
  }

  @Override
  protected Task convert(CacheData cacheData) {
    Task task = new Task();
    Map<String, Object> attributes = cacheData.getAttributes();
    task.setTaskId((String) attributes.get("taskId"));
    task.setTaskArn((String) attributes.get("taskArn"));
    task.setClusterArn((String) attributes.get("clusterArn"));
    task.setContainerInstanceArn((String) attributes.get("containerInstanceArn"));
    task.setGroup((String) attributes.get("group"));
    task.setLastStatus((String) attributes.get("lastStatus"));
    task.setDesiredStatus((String) attributes.get("desiredStatus"));
    task.setStartedAt((Long) attributes.get("startedAt"));

    if (attributes.containsKey("containers")) {
      ObjectMapper mapper = new ObjectMapper();
      List<Map<String, Object>> containers = (List<Map<String, Object>>) attributes.get("containers");
      List<Container> deserializedLoadbalancers = new ArrayList<>(containers.size());

      for (Map<String, Object> serializedContainer : containers) {
        if (serializedContainer != null) {
          deserializedLoadbalancers.add(mapper.convertValue(serializedContainer, Container.class));
        }
      }

      task.setContainers(deserializedLoadbalancers);
    } else {
      task.setContainers(Collections.emptyList());
    }

    return task;
  }
}
