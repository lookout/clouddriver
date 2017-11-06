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

import com.netflix.spinnaker.clouddriver.aws.deploy.description.AbstractAmazonCredentialsDescription;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import org.springframework.validation.Errors;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

abstract class CommonValidator extends DescriptionValidator {

  void validateRegions(AbstractAmazonCredentialsDescription credentialsDescription, Collection<String> regionNames, String errorKey, Errors errors, String attributeName) {
    if (regionNames.isEmpty()) {
      errors.rejectValue(attributeName, errorKey + "." + attributeName + ".empty");
    } else {
      Set<String> validRegions = credentialsDescription.getCredentials().getRegions().stream()
        .map(AmazonCredentials.AWSRegion::getName)
        .collect(Collectors.toSet());

      if (!validRegions.isEmpty() && !validRegions.containsAll(regionNames)) {
        errors.rejectValue(attributeName, errorKey + "." + attributeName + ".not.configured");
      }
    }
  }
}
