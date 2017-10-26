/*
 * Copyright 2017 Lookout, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.deploy.description;

import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = false)
public class CreateServerGroupDescription extends AbstractECSDescription {
  String ecsClusterName;
  String iamRole;
  Integer containerPort;
  List<String> targetGroups;
  List<String> securityGroups;

  String serverGroupVersion;
  String portProtocol;

  Integer computeUnits;
  Integer reservedMemory;

  String dockerImageAddress;

  ServerGroup.Capacity capacity;

  Source source;

  Map<String, List<String>> availabilityZones;

  @Data
  public class Source {
    String asgName, serverGroupName, account, region, useSourceCapacity;
  }
}
