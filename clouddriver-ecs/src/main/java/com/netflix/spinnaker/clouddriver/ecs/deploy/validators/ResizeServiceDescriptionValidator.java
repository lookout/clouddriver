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

package com.netflix.spinnaker.clouddriver.ecs.deploy.validators;

import com.netflix.spinnaker.clouddriver.ecs.EcsOperation;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.ResizeServiceDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

import java.util.Collections;
import java.util.List;

@EcsOperation(AtomicOperations.RESIZE_SERVER_GROUP)
@Component("resizeServiceAtomicOperationValidator")
public class ResizeServiceDescriptionValidator extends CommonValidator {

  @Override
  public void validate(List priorDescriptions, Object description, Errors errors) {
    ResizeServiceDescription typedDescription = (ResizeServiceDescription) description;

    boolean validCredentials = validateCredentials(typedDescription, "resizeServiceDescription", errors, "credentials");

    if (validCredentials) {
      validateRegions(typedDescription, Collections.singleton(typedDescription.getRegion()), "resizeServiceDescription", errors, "region");
    }

    if(typedDescription.getServerGroupName() == null){
      errors.rejectValue("serverGroupName", "resizeServiceDescription.serverGroupName.not.nullable");
    }

    if(typedDescription.getCapacity() != null){
      boolean desiredNotNull = typedDescription.getCapacity().getDesired() != null;
      boolean minNotNull = typedDescription.getCapacity().getMin() != null;
      boolean maxNotNull = typedDescription.getCapacity().getMax() != null;

      if(!desiredNotNull){
        errors.rejectValue("capacity.desired", "resizeServiceDescription.capacity.desired.not.nullable");
      }
      if(!minNotNull){
        errors.rejectValue("capacity.min", "resizeServiceDescription.capacity.min.not.nullable");
      }
      if(!maxNotNull){
        errors.rejectValue("capacity.max", "resizeServiceDescription.capacity.max.not.nullable");
      }

      positivityCheck(desiredNotNull,typedDescription.getCapacity().getDesired(), "desired", errors);
      positivityCheck(minNotNull,typedDescription.getCapacity().getMin(), "min", errors);
      positivityCheck(maxNotNull,typedDescription.getCapacity().getMax(), "max", errors);


      if(minNotNull && maxNotNull){
        if(typedDescription.getCapacity().getMin() > typedDescription.getCapacity().getMax()){
          errors.rejectValue("capacity", "resizeServiceDescription.capacity.invalid.min.max.range");
        }

        if(desiredNotNull && typedDescription.getCapacity().getDesired() > typedDescription.getCapacity().getMax()){
          errors.rejectValue("capacity", "resizeServiceDescription.capacity.desired.exceeds.max");
        }

        if(desiredNotNull && typedDescription.getCapacity().getDesired() < typedDescription.getCapacity().getMin()){
          errors.rejectValue("capacity", "resizeServiceDescription.capacity.desired.less.than.min");
        }
      }

    }else{
      errors.rejectValue("capacity", "resizeServiceDescription.capacity.not.nullable");
    }
  }

  private void positivityCheck(boolean isNotNull, Integer capacity, String fieldName, Errors errors){
    if(isNotNull && capacity < 0){
      errors.rejectValue("capacity."+fieldName, "resizeServiceDescription.capacity."+fieldName+".not.positive");
    }
  }
}
