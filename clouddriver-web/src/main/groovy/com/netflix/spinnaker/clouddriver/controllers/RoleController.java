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

package com.netflix.spinnaker.clouddriver.controllers;

import com.netflix.spinnaker.clouddriver.model.Role;
import com.netflix.spinnaker.clouddriver.model.RoleProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/roles")
public class RoleController {

  @Autowired
  List<RoleProvider> roleProviders;

  @RequestMapping(method = RequestMethod.GET, value = "/{provider}/{account}/{region}")
  Collection<Role> getRoles(@PathVariable String provider, @PathVariable String account, @PathVariable String region) {

    Set<Role> result = new HashSet<>();

    //TODO(Bruno Carrier) - I am sure we can make a nice java Stream that does it more elegantly
    for (RoleProvider roleProvider: roleProviders) {
      if (roleProvider.getCloudProvider().equals(provider)) {
        result.addAll(roleProvider.getAll(account, region));
      }
    }

    return result;
  }
}
