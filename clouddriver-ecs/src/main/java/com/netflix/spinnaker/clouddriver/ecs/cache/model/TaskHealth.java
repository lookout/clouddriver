package com.netflix.spinnaker.clouddriver.ecs.cache.model;

import lombok.Data;

@Data
public class TaskHealth {
  String state;
  String type;
  String serviceName;
  String taskArn;
  String instanceId;
  String taskId;
}
