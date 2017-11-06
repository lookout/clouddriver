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

import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.ecs.EcsOperation;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.DisableServiceDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@EcsOperation(AtomicOperations.DISABLE_SERVER_GROUP)
@Component("disableServiceAtomicOperationValidator")
public class DisableServiceAtomicOperationValidator extends CommonValidator {

  @Override
  public void validate(List priorDescriptions, Object description, Errors errors) {
    DisableServiceDescription disableServiceDescription = (DisableServiceDescription) description;

    if (disableServiceDescription.getServerGroupName() == null) {
      errors.rejectValue("serverGroupName", "disableServiceDescription.serverGroupName.not.nullable");
    }

    validateRegions(disableServiceDescription, Collections.singleton(disableServiceDescription.getRegion()), "disableServiceDescription", errors, "region");
  }
}
