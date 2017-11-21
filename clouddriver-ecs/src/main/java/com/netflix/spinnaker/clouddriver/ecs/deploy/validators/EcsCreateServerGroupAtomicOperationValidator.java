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

import com.amazonaws.services.ecs.model.PlacementStrategy;
import com.amazonaws.services.ecs.model.PlacementStrategyType;
import com.google.common.collect.Sets;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.ecs.EcsOperation;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.CreateServerGroupDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

import java.util.List;
import java.util.Set;

@EcsOperation(AtomicOperations.CREATE_SERVER_GROUP)
@Component("ecsCreateServerGroupAtomicOperationValidator")
public class EcsCreateServerGroupAtomicOperationValidator extends DescriptionValidator {

  private static final Set<String> BINPACK_VALUES = Sets.newHashSet("cpu", "memory");
  private static final Set<String> SPREAD_VALUES = Sets.newHashSet(
    "instanceId",
    "attribute:ecs.availability-zone",
    "attribute:ecs.instance-type",
    "attribute:ecs.os-type",
    "attribute:ecs.ami-id"
  );

  @Override
  public void validate(List priorDescriptions, Object description, Errors errors) {
    CreateServerGroupDescription createServerGroupDescription = (CreateServerGroupDescription) description;

    if (createServerGroupDescription.getPlacementStrategySequence() != null) {
      for (PlacementStrategy placementStrategy : createServerGroupDescription.getPlacementStrategySequence()) {
        PlacementStrategyType type;
        try {
          type = PlacementStrategyType.fromValue(placementStrategy.getType());
        } catch (IllegalArgumentException e) {
          errors.rejectValue("placementStrategySequence", "createServerGroupDescription.placementStrategySequence.invalid.type");
          continue;
        }

        switch (type) {
          case Random:
            if (placementStrategy.getField().length() != 0) {
              errors.rejectValue("placementStrategySequence", "createServerGroupDescription.placementStrategySequence.invalid.random.value");
            }
            break;
          case Spread:
            if (!SPREAD_VALUES.contains(placementStrategy.getField())) {
              errors.rejectValue("placementStrategySequence", "createServerGroupDescription.placementStrategySequence.invalid.spread.value");
            }
            break;
          case Binpack:
            if (!BINPACK_VALUES.contains(placementStrategy.getField())) {
              errors.rejectValue("placementStrategySequence", "createServerGroupDescription.placementStrategySequence.invalid.binpack.value");
            }
            break;
        }

      }
    } else {
      // This only applies to pipelines that have been created before support for placement strategies and have not been updated since.
      errors.rejectValue("placementStrategySequence", "createServerGroupDescription.placementStrategySequence.null");
    }

  }

}
