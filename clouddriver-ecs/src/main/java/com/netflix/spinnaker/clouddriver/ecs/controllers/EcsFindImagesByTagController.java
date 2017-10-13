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

package com.netflix.spinnaker.clouddriver.ecs.controllers;

import com.amazonaws.services.ecr.AmazonECR;
import com.amazonaws.services.ecr.model.DescribeImagesRequest;
import com.amazonaws.services.ecr.model.DescribeImagesResult;
import com.amazonaws.services.ecr.model.ImageDetail;
import com.amazonaws.services.ecr.model.ListImagesRequest;
import com.amazonaws.services.ecr.model.ListImagesResult;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/ecs")
public class EcsFindImagesByTagController {
  private static final Pattern ACCOUNT_ID_PATTERN = Pattern.compile("^([0-9]{12})");
  private static final Pattern REPOSITORY_NAME_PATTERN = Pattern.compile("\\/([a-z0-9._-]+)");
  private static final String IDENTIFIER_PATTERN = "(:([a-z0-9._-]+)|@(sha256:[0-9a-f]{64}))";
  private static final Pattern REPOSITORY_URI_PATTERN = Pattern.compile(ACCOUNT_ID_PATTERN.toString()+".+"+REPOSITORY_NAME_PATTERN.toString()+IDENTIFIER_PATTERN);

  AmazonClientProvider amazonClientProvider;

  AccountCredentialsProvider accountCredentialsProvider;

  @Autowired
  public EcsFindImagesByTagController(AmazonClientProvider amazonClientProvider,
                       AccountCredentialsProvider accountCredentialsProvider) {
    this.amazonClientProvider = amazonClientProvider;
    this.accountCredentialsProvider = accountCredentialsProvider;
  }

  private NetflixAmazonCredentials getCredentials(String accountId) {
    for (AccountCredentials credentials: accountCredentialsProvider.getAll()) {
      if (credentials instanceof NetflixAmazonCredentials) {
        NetflixAmazonCredentials amazonCredentials = (NetflixAmazonCredentials) credentials;
        if (amazonCredentials.getAccountId().equals(accountId)) {
          return amazonCredentials;
        }
      }
    }



    throw new NotFoundException(String.format("AWS account %s was not found.  Please specify a valid account name"));
  }

  @RequestMapping(value = "/images/find", method = RequestMethod.GET)
  public Object findImage(@RequestParam("q") String dockerImageUrl, HttpServletRequest request) {
    Matcher matcher = ACCOUNT_ID_PATTERN.matcher(dockerImageUrl);
    if (!matcher.find()) {
      throw new Error("The repository URI provided does not contain a proper account ID.");
    }
    String accountId = matcher.group(1);

    matcher = REPOSITORY_NAME_PATTERN.matcher(dockerImageUrl);
    if (!matcher.find()) {
      throw new Error("The repository URI provided does not contain a proper repository name.");
    }
    String repository = matcher.group(1);

    final Pattern identifierPatter = Pattern.compile(repository+IDENTIFIER_PATTERN);
    matcher = identifierPatter.matcher(dockerImageUrl);
    if (!matcher.find()) {
      throw new Error("The repository URI provided does not contain a proper tag or sha256 digest.");
    }
    final boolean isTag = matcher.group(1).startsWith(":");
    final String identifier = isTag ? matcher.group(2) : matcher.group(3);

    matcher = REPOSITORY_URI_PATTERN.matcher(dockerImageUrl);
    if (!matcher.find()) {
      throw new Error("The repository URI provided is not properly structured.");
    }

    NetflixAmazonCredentials credentials = getCredentials(accountId);

    AmazonECR amazonECR = amazonClientProvider.getAmazonEcr(credentials.getName(), credentials.getCredentialsProvider(), "us-west-2");

    ListImagesResult result = amazonECR.listImages(new ListImagesRequest().withRegistryId(accountId).withRepositoryName(repository));
    DescribeImagesResult imagesResult = amazonECR.describeImages(new DescribeImagesRequest().withRegistryId(accountId).withRepositoryName(repository).withImageIds(result.getImageIds()));

    List<ImageDetail> imagesWithThisIdentifier = imagesResult.getImageDetails().stream()
      .filter(imageDetail -> isTag ? imageDetail.getImageTags().contains(identifier) : // TODO - what is the user interface we want to have here?  We should discuss with Lars and Ethan from the community as this whole thing will undergo a big refactoring
                                     imageDetail.getImageDigest().equals(identifier))
      .collect(Collectors.toList());

    if (imagesWithThisIdentifier.size() > 1) {
      throw new Error("More than 1 image has this " + (isTag ? "tag" : "digest") + "!  We can't handle this in the POC!");
    } else if (imagesWithThisIdentifier.size() == 0) {
      throw new Error(String.format("No image with the " + (isTag ? "tag" : "digest") + " %s was found.", identifier));
    }


    ImageDetail matchedImage = imagesWithThisIdentifier.get(0);

    List<Map<String, Object>> responseBody = new ArrayList<>();
    Map<String, Object> map = new HashMap<>();
    map.put("imageName", buildFullDockerImageUrl(matchedImage.getImageDigest(),
      matchedImage.getRegistryId(),
      matchedImage.getRepositoryName(),
      "us-west-2"));  // TODO - this is so cheesy, uncheese this

    Map<String, List<String>> amis = new HashMap<>();
    amis.put("us-west-2", Arrays.asList(matchedImage.getImageDigest()));
    map.put("amis", amis);

    responseBody.add(map);

    return responseBody;
  }


  private String buildFullDockerImageUrl(String imageDigest, String registryId, String repositoryName, String region) {
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(registryId)
      .append(".dkr.ecr.")
      .append(region)
      .append(".amazonaws.com/")
      .append(repositoryName)
      .append("@")
      .append(imageDigest);
    return stringBuilder.toString();
  }
}
