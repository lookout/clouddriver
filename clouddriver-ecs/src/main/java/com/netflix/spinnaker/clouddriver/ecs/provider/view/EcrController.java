package com.netflix.spinnaker.clouddriver.ecs.provider.view;

import com.amazonaws.services.ecr.AmazonECR;
import com.amazonaws.services.ecr.model.*;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ecr/{account}/{region}")
public class EcrController {

  AmazonClientProvider amazonClientProvider;

  AccountCredentialsProvider accountCredentialsProvider;

  @Autowired
  public EcrController(AmazonClientProvider amazonClientProvider,
                       AccountCredentialsProvider accountCredentialsProvider) {
    this.amazonClientProvider = amazonClientProvider;
    this.accountCredentialsProvider = accountCredentialsProvider;
  }

  private NetflixAmazonCredentials getCredentials(String account) {
    AccountCredentials accountCredentials = accountCredentialsProvider.getCredentials(account);

    if (accountCredentials instanceof NetflixAmazonCredentials) {
      return (NetflixAmazonCredentials) accountCredentials;
    }

    throw new NotFoundException(String.format("AWS account %s was not found.  Please specify a valid account name"));
  }

  @RequestMapping(value = "/repository", method = RequestMethod.GET)
  public List<Repository> findAllRepositories(@PathVariable("account") String account,
                                              @PathVariable("region") String region) {
    AmazonECR amazonECR = amazonClientProvider.getAmazonEcr(account, getCredentials(account).getCredentialsProvider(), region);

    DescribeRepositoriesResult result = amazonECR.describeRepositories(new DescribeRepositoriesRequest());

    return result.getRepositories();
  }

  @RequestMapping(value = "/{repository}/image", method = RequestMethod.GET)
  public List<ImageDetail> findAllImages(@PathVariable("account") String account,
                                            @PathVariable("region") String region,
                                            @PathVariable("repository") String repositoryName) {
    AmazonECR amazonECR = amazonClientProvider.getAmazonEcr(account, getCredentials(account).getCredentialsProvider(), region);

    DescribeImagesRequest request = new DescribeImagesRequest();
    request.setRepositoryName(repositoryName);

    DescribeImagesResult result = amazonECR.describeImages(request);

    return result.getImageDetails();
  }

}
