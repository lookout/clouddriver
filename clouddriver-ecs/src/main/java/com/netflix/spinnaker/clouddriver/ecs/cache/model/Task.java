package com.netflix.spinnaker.clouddriver.ecs.cache.model;

import com.amazonaws.services.ecs.model.Container;
import lombok.Data;

import java.util.List;

@Data
public class Task {
  String taskId;
  String taskArn;
  String clusterArn;
  String containerInstanceArn;
  String group;
  String lastStatus;
  String desiredStatus;
  long startedAt;
  List<Container> containers;
}