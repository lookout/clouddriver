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

package com.netflix.spinnaker.clouddriver.ecs.deploy.validators

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator
import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.AbstractECSDescription
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.ModifyServiceDescription
import org.springframework.validation.Errors

class ServerGroupDescriptionValidatorSpec extends AbstractValidatorSpec {

  void 'should fail null serverGroupName'() {
    given:
    def description = (ModifyServiceDescription) getDescription()
    description.serverGroupName = null
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('serverGroupName', _)
  }

  @Override
  DescriptionValidator getDescriptionValidator() {
    new ServerGroupDescriptionValidator()
  }

  @Override
  AbstractECSDescription getDescription() {
    def description = new ModifyServiceDescription()
    description.credentials = TestCredential.named('test')
    description.region = 'us-west-1'
    description.serverGroupName = 'test'
    description
  }
}
