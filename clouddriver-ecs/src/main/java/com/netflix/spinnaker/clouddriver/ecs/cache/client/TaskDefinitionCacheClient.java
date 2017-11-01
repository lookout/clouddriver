package com.netflix.spinnaker.clouddriver.ecs.cache.client;

import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.TASK_DEFINITIONS;

public class TaskDefinitionCacheClient extends AbstractCacheClient<TaskDefinition> {

  @Autowired
  public TaskDefinitionCacheClient(Cache cacheView) {
    super(cacheView, TASK_DEFINITIONS.toString());
  }

  @Override
  protected TaskDefinition convert(CacheData cacheData) {
    TaskDefinition taskDefinition = new TaskDefinition();
    Map<String, Object> attributes = cacheData.getAttributes();

    taskDefinition.setTaskDefinitionArn((String) attributes.get("taskDefinitionArn"));

    if (attributes.containsKey("containerDefinitions")) {
      ObjectMapper mapper = new ObjectMapper();
      List<Map<String, Object>> containerDefinitions = (List<Map<String, Object>>) attributes.get("containerDefinitions");
      List<ContainerDefinition> deserializedContainerDefinitions = new ArrayList<>(containerDefinitions.size());

      for (Map<String, Object> serializedContainerDefinitions : containerDefinitions) {
        if (serializedContainerDefinitions != null) {
          deserializedContainerDefinitions.add(mapper.convertValue(serializedContainerDefinitions, ContainerDefinition.class));
        }
      }

      taskDefinition.setContainerDefinitions(deserializedContainerDefinitions);
    } else {
      taskDefinition.setContainerDefinitions(Collections.emptyList());
    }

    return taskDefinition;
  }
}
