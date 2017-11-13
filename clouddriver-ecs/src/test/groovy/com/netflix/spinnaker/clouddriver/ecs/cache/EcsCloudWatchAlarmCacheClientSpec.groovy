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

package com.netflix.spinnaker.clouddriver.ecs.cache

import com.amazonaws.services.cloudwatch.model.MetricAlarm
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.clouddriver.ecs.cache.client.EcsCloudWatchAlarmCacheClient
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.EcsCloudMetricAlarmCachingAgent
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ALARMS

class EcsCloudWatchAlarmCacheClientSpec extends Specification {
  def cacheView = Mock(Cache)
  @Subject
  EcsCloudWatchAlarmCacheClient client = new EcsCloudWatchAlarmCacheClient(cacheView)

  def 'should convert cache data into object'() {
    given:
    def metricAlarm = new MetricAlarm().withAlarmName("alarm-name").withAlarmArn("alarmArn")
    def key = Keys.getAlarmKey('test-account-1', 'us-west-1', metricAlarm.getAlarmArn())
    def attributes = EcsCloudMetricAlarmCachingAgent.convertMetricAlarmToAttributes(metricAlarm)

    when:
    def returnedMetricAlarm = client.get(key)

    then:
    cacheView.get(ALARMS.ns, key) >> new DefaultCacheData(key, attributes, [:])
    returnedMetricAlarm == metricAlarm
  }
}
