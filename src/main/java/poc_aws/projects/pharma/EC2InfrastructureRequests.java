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

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClientBuilder;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest;

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
    private AmazonEC2 ec2Client;

    /** load balancer client*/
    private AmazonElasticLoadBalancing lbClient;

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
        AWSCredentials credentials = new ProfileCredentialsProvider().getCredentials();
        ec2Client = AmazonEC2ClientBuilder.standard().withRegion(Regions.US_EAST_1)
                .withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
        lbClient = AmazonElasticLoadBalancingClientBuilder.standard().withRegion(Regions.US_EAST_1)
                .withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
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
        DescribeInstancesRequest request = new DescribeInstancesRequest().withInstanceIds(instanceId);
        DescribeInstancesResult response = ec2Client.describeInstances(request);
        for (Reservation reservation : response.getReservations()) {
            for (Instance instance : reservation.getInstances()) {
                System.out.println("instance id =" + instance.getInstanceId());
                System.out.println("imageId = " + instance.getImageId());
                System.out.println("instance type = " +instance.getInstanceType());
                System.out.println("instance architecture = " + instance.getArchitecture());
                System.out.println("IAM arn = "  + instance.getIamInstanceProfile().getArn());
                System.out.println("Key name = " + instance.getKeyName());
                System.out.println("subnet Id  = " + instance.getSubnetId());
                System.out.println("VPC Id  = " + instance.getVpcId());
                System.out.println("Security Groups = " + instance.getSecurityGroups());
                System.out.println("");
            }
        }
    }

    /**
     * find and print the load ballancer which is assigned to  the required instance.
     */
    public void findLoadBalancer() {

        List<LoadBalancerDescription> descriptions  = lbClient.describeLoadBalancers(new DescribeLoadBalancersRequest())
                .getLoadBalancerDescriptions();
        for (LoadBalancerDescription description : descriptions) {
            for (com.amazonaws.services.elasticloadbalancing.model.Instance instance : description.getInstances()) {
                if (instance.getInstanceId().equals(instanceId)) {
                    System.out.println("Assigned load balancer is :" + description.getLoadBalancerName());
                    loadBalancerName = description.getLoadBalancerName();
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
        DescribeInstancesResult response = ec2Client.describeInstances(new DescribeInstancesRequest().withInstanceIds(instanceId));
        for (Reservation reservation : response.getReservations()) {
            for (Instance instance : reservation.getInstances()) {
                if(instanceId.equals(instance.getInstanceId())) {
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
        instanceToClone.getSecurityGroups().stream().forEach(group -> securityGroupIds.add(group.getGroupId()));
        RunInstancesRequest request = new RunInstancesRequest().withMinCount(1).withMaxCount(1);
        request.withImageId(instanceToClone.getImageId()).withInstanceType(instanceToClone.getInstanceType()).withKeyName(instanceToClone.getKeyName());
        //do not work with security group and subnets.
        //.withSecurityGroupIds(securityGroupId);
        List<InstanceNetworkInterfaceSpecification> interfaces = new ArrayList<>();
        InstanceNetworkInterfaceSpecification interfaceDNS = new InstanceNetworkInterfaceSpecification();
        interfaceDNS.withSubnetId(instanceToClone.getSubnetId()).withAssociatePublicIpAddress(true).setDeviceIndex(0);
        interfaceDNS.setGroups(securityGroupIds);
        interfaces.add(interfaceDNS);
        request.withNetworkInterfaces(interfaces).withAdditionalInfo("--associate-public-ip-address");
        if (installingScript != null) {
            request.withUserData(Base64.getEncoder().encodeToString(installingScript.getBytes()));
        }
        IamInstanceProfileSpecification profile = new IamInstanceProfileSpecification().withArn(instanceToClone.getIamInstanceProfile().getArn());
        request.withIamInstanceProfile(profile);
        RunInstancesResult result = ec2Client.runInstances(request);
        CreateTagsRequest tagNameRequest = new CreateTagsRequest().withResources(result.getReservation().getInstances().get(0).getInstanceId());
        tagNameRequest.withTags(new Tag().withKey("Name").withValue("cloned"));
        ec2Client.createTags(tagNameRequest);
        return result.getReservation().getInstances().get(0).getInstanceId();
    }

    /**
     * add the instance to the loadbalancer
     * @param instanceId the instance id
     */
    public void addInstacesToLB(String instanceId) {
        RegisterInstancesWithLoadBalancerRequest request = new RegisterInstancesWithLoadBalancerRequest();
        List<com.amazonaws.services.elasticloadbalancing.model.Instance> instances = new ArrayList<>();
        instances.add(new com.amazonaws.services.elasticloadbalancing.model.Instance(instanceId));
        request.withLoadBalancerName(loadBalancerName).withInstances(instances);
        lbClient.registerInstancesWithLoadBalancer(request);
    }
}
