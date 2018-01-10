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
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.ResizeServiceDescription
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.TerminateInstancesDescription
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import org.springframework.validation.Errors

class ResizeDescriptionValidatorSpec extends AbstractValidatorSpec {

  void 'should fail when capacity is null'() {
    given:
    def description = (ResizeServiceDescription) getDescription()
    description.capacity = null
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('capacity', 'resizeServiceDescription.capacity.not.nullable')
  }

  void 'should fail when desired is null'() {
    given:
    def description = (ResizeServiceDescription) getDescription()
    description.capacity.setDesired(null)
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('capacity.desired', 'resizeServiceDescription.capacity.desired.not.nullable')
  }

  void 'should fail when min is null'() {
    given:
    def description = (ResizeServiceDescription) getDescription()
    description.capacity.setMin(null)
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('capacity.min', 'resizeServiceDescription.capacity.min.not.nullable')
  }

  void 'should fail when max is null'() {
    given:
    def description = (ResizeServiceDescription) getDescription()
    description.capacity.setMax(null)
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('capacity.max', 'resizeServiceDescription.capacity.max.not.nullable')
  }

  void 'should fail when desired is negative'() {
    given:
    def description = (ResizeServiceDescription) getDescription()
    description.capacity.setDesired(-1)
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('capacity.desired', 'resizeServiceDescription.capacity.desired.not.positive')
  }

  void 'should fail when min is negative'() {
    given:
    def description = (ResizeServiceDescription) getDescription()
    description.capacity.setMin(-1)
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('capacity.min', 'resizeServiceDescription.capacity.min.not.positive')
  }

  void 'should fail when max is negative'() {
    given:
    def description = (ResizeServiceDescription) getDescription()
    description.capacity.setMax(-1)
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('capacity.max', 'resizeServiceDescription.capacity.max.not.positive')
  }

  void 'should fail when min-max range is invalid'() {
    given:
    def description = (ResizeServiceDescription) getDescription()
    description.capacity.setMax(1)
    description.capacity.setMin(2)
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('capacity', 'resizeServiceDescription.capacity.invalid.min.max.range')
  }

  void 'should fail when desired is greater than max'() {
    given:
    def description = (ResizeServiceDescription) getDescription()
    description.capacity.setDesired(9001)
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('capacity', 'resizeServiceDescription.capacity.desired.exceeds.max')
  }

  void 'should fail when desired is less than min'() {
    given:
    def description = (ResizeServiceDescription) getDescription()
    description.capacity.setDesired(0)
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('capacity', 'resizeServiceDescription.capacity.desired.less.than.min')
  }

  @Override
  DescriptionValidator getDescriptionValidator() {
    new ResizeServiceDescriptionValidator()
  }

  @Override
  AbstractECSDescription getDescription() {
    def description = new ResizeServiceDescription()
    description.credentials = TestCredential.named('test')
    description.region = 'us-west-1'
    description.serverGroupName = 'myapp-kcats-liated-v007'
    description.capacity = new ServerGroup.Capacity(
      desired: 1,
      min: 1,
      max: 2
    )
    description
  }
}
