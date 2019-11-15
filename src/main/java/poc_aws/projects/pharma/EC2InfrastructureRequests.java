/*
 Copyright (c) 2019 Gabriel Dimitriu All rights reserved.
 DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.

 This file is part of poc_aws project.

 poc_aws is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 poc_aws is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with poc_aws.  If not, see <http://www.gnu.org/licenses/>.
 */
package poc_aws.projects.pharma;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.elasticloadbalancing.ElasticLoadBalancingClient;
import software.amazon.awssdk.services.elasticloadbalancing.model.DescribeLoadBalancersRequest;
import software.amazon.awssdk.services.elasticloadbalancing.model.LoadBalancerDescription;
import software.amazon.awssdk.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

public class EC2InfrastructureRequests {
    /** ec2 client for amazon instances */
    private Ec2Client ec2Client;

    /** load balancer client*/
    private ElasticLoadBalancingClient lbClient;

    /** the instance id on which we will have the processing */
    private String instanceId;

    /** true if this program is running inside AWS EC2 machine, false otherwise */
    private boolean isRunningInAWSEC2 = false;

    private String loadBalancerName = null;

    /** name of the bucket where the website sources will reside */
    private static final String ROOT_BUCKET_DELIVERY_NAME = "gabrieldimitriupharmaweb";
    /** Install script for the first web server */
    private static final String INSTALL_SCRIPT_3="#!/bin/bash\n" +
            "exec > /tmp/start.log  2>&1\n" +
            "sudo yum update -y\n" +
            "sudo yum install httpd -y\n" +
            "sudo chkconfig httpd on\n" +
            "cd /home/ec2-user\n" +
            "aws s3 cp s3://" + ROOT_BUCKET_DELIVERY_NAME + "/pharma_webservers/web3.zip web.zip\n" +
            "sudo unzip web.zip -d /var/www/html/\n" +
            "rm web.zip\n" +
            "aws s3 cp s3://" + ROOT_BUCKET_DELIVERY_NAME + "/pharma_webservers/welcome.conf welcome.conf\n" +
            "sudo cp welcome.conf /etc/httpd/conf.d/\n" +
            "rm welcome.conf\n" +
            "sudo /etc/init.d/httpd start\n";

    public static void main(String...args) {
        EC2InfrastructureRequests infra = new EC2InfrastructureRequests();
        infra.findAndSetInstance();
        infra.describeInstances();
        infra.findLoadBalancer();
        String instanceID = infra.cloneInstance(INSTALL_SCRIPT_3);
        System.out.println("InstanceId clonned = " + instanceID);
        infra.addInstacesToLB(instanceID);
    }

    /**
     * constructor to initialize the ec2 client and elastic load balancer client.
     */
    public EC2InfrastructureRequests() {
        AwsCredentials credentials = ProfileCredentialsProvider.builder().build().resolveCredentials();
        ec2Client = Ec2Client.builder().credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.US_EAST_1)
                .httpClientBuilder(UrlConnectionHttpClient.builder()).build();
        lbClient = ElasticLoadBalancingClient.builder().credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.US_EAST_1)
                .httpClientBuilder(UrlConnectionHttpClient.builder()).build();
    }

    /** find and set the instance id  */
    public void findAndSetInstance() {
        //find if we are into AWS instance
        try {
            URL url = new URL("http://169.254.169.254/latest/meta-data/instance-id");
            //should be only one line
            instanceId =  new BufferedReader(new InputStreamReader(url.openConnection().getInputStream())).lines().collect(Collectors.joining(""));
            isRunningInAWSEC2 = true;
        } catch (IOException ex) {
            System.out.println("We are running outside AWS EC2 machine !");
            instanceId = null;
        }
        if (instanceId == null) {
            System.out.println("Please provide the instanceId of the EC2 machine");
            Scanner scanner = new Scanner(System.in);
            instanceId = scanner.nextLine();
        }
    }

    /**
     *  describe the instances with all parameters.
     * */
    public void describeInstances() {
        DescribeInstancesRequest request = DescribeInstancesRequest.builder().instanceIds(instanceId).build();
        DescribeInstancesResponse response = ec2Client.describeInstances(request);
        for (Reservation reservation : response.reservations()) {
            for (Instance instance : reservation.instances()) {
                System.out.println("instance id =" + instance.instanceId());
                System.out.println("imageId = " + instance.imageId());
                System.out.println("instance type = " +instance.instanceType());
                System.out.println("instance architecture = " + instance.architecture());
                System.out.println("IAM arn = "  + instance.iamInstanceProfile().arn());
                System.out.println("Key name = " + instance.keyName());
                System.out.println("subnet Id  = " + instance.subnetId());
                System.out.println("VPC Id  = " + instance.vpcId());
                System.out.println("Security Groups = " + instance.securityGroups());
                System.out.println("");
            }
        }
    }

    /**
     * find and print the load ballancer which is assigned to  the required instance.
     */
    public void findLoadBalancer() {

        List<LoadBalancerDescription> descriptions  = lbClient.describeLoadBalancers(DescribeLoadBalancersRequest.builder().build())
                .loadBalancerDescriptions();
        for (LoadBalancerDescription description : descriptions) {
            for (software.amazon.awssdk.services.elasticloadbalancing.model.Instance instance : description.instances()) {
                if (instance.instanceId().equals(instanceId)) {
                    System.out.println("Assigned load balancer is :" + description.loadBalancerName());
                    loadBalancerName = description.loadBalancerName();
                    return;
                }
            }
        }
    }

    /**
     * get the instance data from instanceId
     * @return the Instance
     */
    private Instance getInstanceFromId() {
        DescribeInstancesResponse response = ec2Client.describeInstances(DescribeInstancesRequest.builder().instanceIds(instanceId).build());
        for (Reservation reservation : response.reservations()) {
            for (Instance instance : reservation.instances()) {
                if(instanceId.equals(instance.instanceId())) {
                    return instance;
                }
            }
        }
        return null;
    }

    /**
     * clone an instance with specific installing script
     * @param installingScript the install script
     * @return the instanceId
     */
    public String cloneInstance(String installingScript) {
        Instance instanceToClone = getInstanceFromId();
        if (instanceToClone == null) {
            return null;
        }
        List<String> securityGroupIds = new ArrayList<>();
        instanceToClone.securityGroups().stream().forEach(group -> securityGroupIds.add(group.groupId()));
        RunInstancesRequest.Builder request = RunInstancesRequest.builder().minCount(1).maxCount(1);
        request.imageId(instanceToClone.imageId()).instanceType(instanceToClone.instanceType()).keyName(instanceToClone.keyName());
        //do not work with security group and subnets.
        //.withSecurityGroupIds(securityGroupId);
        List<InstanceNetworkInterfaceSpecification> interfaces = new ArrayList<>();
        InstanceNetworkInterfaceSpecification.Builder interfaceDNS = InstanceNetworkInterfaceSpecification.builder();
        interfaceDNS.subnetId(instanceToClone.subnetId()).associatePublicIpAddress(true).deviceIndex(0);
        interfaceDNS.groups(securityGroupIds);
        interfaces.add(interfaceDNS.build());
        request.networkInterfaces(interfaces).additionalInfo("--associate-public-ip-address");
        if (installingScript != null) {
            request.userData(Base64.getEncoder().encodeToString(installingScript.getBytes()));
        }
        IamInstanceProfileSpecification profile = IamInstanceProfileSpecification.builder().arn(instanceToClone.iamInstanceProfile().arn()).build();
        request.iamInstanceProfile(profile);
        RunInstancesResponse result = ec2Client.runInstances(request.build());
        CreateTagsRequest.Builder tagNameRequest = CreateTagsRequest.builder().resources(result.instances().get(0).instanceId());
        tagNameRequest.tags(Tag.builder().key("Name").value("cloned").build());
        ec2Client.createTags(tagNameRequest.build());
        return result.instances().get(0).instanceId();
    }

    /**
     * add the instance to the loadbalancer
     * @param instanceId the instance id
     */
    public void addInstacesToLB(String instanceId) {
        DescribeInstancesRequest describeRequest = DescribeInstancesRequest.builder().instanceIds(instanceId).build();
        DescribeInstancesResponse describeResult = null;
        do {
            describeResult = ec2Client.describeInstances(describeRequest);
        } while (describeResult.reservations().get(0).instances().get(0).state().code() != 16);

        List<software.amazon.awssdk.services.elasticloadbalancing.model.Instance> instances = new ArrayList<>();
        instances.add(software.amazon.awssdk.services.elasticloadbalancing.model.Instance.builder().instanceId(instanceId).build());
        RegisterInstancesWithLoadBalancerRequest request = RegisterInstancesWithLoadBalancerRequest.builder()
                .loadBalancerName(loadBalancerName).instances(instances).build();
        lbClient.registerInstancesWithLoadBalancer(request);
    }
}
