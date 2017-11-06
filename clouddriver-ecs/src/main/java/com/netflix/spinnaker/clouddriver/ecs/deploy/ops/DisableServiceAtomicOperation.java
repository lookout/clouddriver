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

package com.netflix.spinnaker.clouddriver.ecs.deploy.ops;

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.UpdateServiceRequest;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.DisableServiceDescription;

import java.util.List;

// TODO: DisableServiceAtomicOperation should not be resizing the service to 0 tasks. It should do something such as removing the instance from the target group.
public class DisableServiceAtomicOperation extends AbstractEcsAtomicOperation<DisableServiceDescription, Void> {

  public DisableServiceAtomicOperation(DisableServiceDescription description) {
    super(description, "DISABLE_ECS_SERVER_GROUP");
  }

  @Override
  public Void operate(List priorOutputs) {
    updateTaskStatus("Initializing Disable Amazon ECS Server Group Operation...");
    disableService();
    return null;
  }

  private void disableService() {
    AmazonECS ecs = getAmazonEcsClient();

    String service = description.getServerGroupName();
    String account = description.getCredentialAccount();
    //TODO: Remove the if statement once the proper account is being passed in.
    if(account.equals("continuous-delivery")){
      account += "-ecs";
    }
    String cluster = getCluster(service, account);

    updateTaskStatus(String.format("Disabling %s service for %s.", service, account));
    UpdateServiceRequest request = new UpdateServiceRequest()
      .withCluster(cluster)
      .withService(service)
      .withDesiredCount(0);
    ecs.updateService(request);
    updateTaskStatus(String.format("Service %s disabled for %s.", service, account));
  }
}
