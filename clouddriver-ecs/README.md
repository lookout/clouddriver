## AWS ECS Clouddriver 

The clouddriver-ecs module allows for ECS deployments of dockerized applications.  **You need to enable the AWS cloud provider in order for the ECS cloud provider to work**.

It is a work in progress




## Spinnaker role
Make sure that you allow the `application-autoscaling.amazonaws.com` and `ecs.amazonaws.com` principals to assume the SpinnakerManaged role by adding it as a principal.  See example code below.  Failure to do so will prevent you from deploying ECS server groups:
```
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
                "Service": [
                  "ecs.amazonaws.com",
                  "application-autoscaling.amazonaws.com"
                ],
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
```
## 


TODO Wishlist:
1. Perhaps clouddriver should try to add the 2 required trust relationships on startup if they are detected as not being present
