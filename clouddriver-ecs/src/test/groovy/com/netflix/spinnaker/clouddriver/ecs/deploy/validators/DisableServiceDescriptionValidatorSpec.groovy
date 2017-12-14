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

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.ecs.TestCredential
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.DisableServiceDescription
import org.springframework.validation.Errors
import spock.lang.Specification
import spock.lang.Subject

class DisableServiceDescriptionValidatorSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  @Subject
  DisableServiceDescriptionValidator validator = new DisableServiceDescriptionValidator();

  void 'should fail empty description validation'() {
    given:
    def description = new DisableServiceDescription()
    description.credentials = TestCredential.named('test')
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('serverGroupName', _)
  }

  void 'should fail an incorrect region'() {
    given:
    def description = new DisableServiceDescription()
    description.credentials = TestCredential.named('test')
    description.serverGroupName = 'test'
    description.region = 'wrong-region-test'
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('region', _)
  }

  void 'should fail on missing credentials'() {
    given:
    def description = new DisableServiceDescription()
    description.serverGroupName = 'test'
    description.region = 'us-west-1'
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    1 * errors.rejectValue('credentials', _)
  }

  void 'should pass validation'() {
    given:
    def description = new DisableServiceDescription()
    description.credentials = TestCredential.named('test')
    description.serverGroupName = 'test'
    description.region = 'us-west-1'
    def errors = Mock(Errors)

    when:
    validator.validate([], description, errors)

    then:
    0 * errors.rejectValue(_, _)
  }

}
