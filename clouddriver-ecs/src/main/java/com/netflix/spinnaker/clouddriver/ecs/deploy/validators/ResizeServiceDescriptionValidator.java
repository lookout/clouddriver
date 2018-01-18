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

  public ResizeServiceDescriptionValidator() {
    super("resizeServiceDescription");
  }

  @Override
  public void validate(List priorDescriptions, Object description, Errors errors) {
    ResizeServiceDescription typedDescription = (ResizeServiceDescription) description;

    boolean validCredentials = validateCredentials(typedDescription, errors, "credentials");

    if (validCredentials) {
      validateRegions(typedDescription, Collections.singleton(typedDescription.getRegion()), errors, "region");
    }

    if(typedDescription.getServerGroupName() == null){
      rejectValue(errors, "serverGroupName", "not.nullable");
    }

    if(typedDescription.getCapacity() != null){
      boolean desiredNotNull = typedDescription.getCapacity().getDesired() != null;
      boolean minNotNull = typedDescription.getCapacity().getMin() != null;
      boolean maxNotNull = typedDescription.getCapacity().getMax() != null;

      if(!desiredNotNull){
        rejectValue(errors, "capacity.desired", "not.nullable");
      }
      if(!minNotNull){
        rejectValue(errors, "capacity.min", "not.nullable");
      }
      if(!maxNotNull){
        rejectValue(errors, "capacity.max", "not.nullable");
      }

      positivityCheck(desiredNotNull,typedDescription.getCapacity().getDesired(), "desired", errors);
      positivityCheck(minNotNull,typedDescription.getCapacity().getMin(), "min", errors);
      positivityCheck(maxNotNull,typedDescription.getCapacity().getMax(), "max", errors);


      if(minNotNull && maxNotNull){
        if(typedDescription.getCapacity().getMin() > typedDescription.getCapacity().getMax()){
          rejectValue(errors, "capacity.min.max.range", "invalid");
        }

        if(desiredNotNull && typedDescription.getCapacity().getDesired() > typedDescription.getCapacity().getMax()){
          rejectValue(errors, "capacity.desired", "exceeds.max");
        }

        if(desiredNotNull && typedDescription.getCapacity().getDesired() < typedDescription.getCapacity().getMin()){
          rejectValue(errors, "capacity.desired", "less.than.min");
        }
      }

    }else{
      rejectValue(errors, "capacity", "not.nullable");
    }
  }

  private void positivityCheck(boolean isNotNull, Integer capacity, String fieldName, Errors errors){
    if(isNotNull && capacity < 0){
      rejectValue(errors, "capacity."+fieldName, "not.positive");
    }
  }
}
