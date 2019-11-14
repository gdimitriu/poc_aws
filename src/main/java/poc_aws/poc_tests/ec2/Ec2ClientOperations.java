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

package poc_aws.poc_tests.ec2;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Ec2ClientOperations {
    /** ec2 client  */
    private Ec2Client ec2Client;
    /** security group for EC2 */
    private CreateSecurityGroupResponse sgResult;

    private static final String KEY_PAIR_NAME="javaAutoKeyPair";

    private static final String SECURITY_GROUP_NAME = "MySecurityGroup";

    private CreateKeyPairResponse keyPair;

    public static void main(String...args) {
        Ec2ClientOperations client = new Ec2ClientOperations();
        client.createSecurityGroup(SECURITY_GROUP_NAME, "My secutity group");
        client.addFirewallRule(SECURITY_GROUP_NAME, Arrays.asList(new String[]{"0.0.0.0/0"}),"tcp",22,22);
        client.createKeyPair(KEY_PAIR_NAME, true);
        try {
            client.savePEMToFile("d:\\" + KEY_PAIR_NAME +".pem");
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Reserved instances!");
        client.printAllInstances();
        System.out.println("Try to start new instance!");
        String instanceId = client.runInstance("MyInstance", "ami-00eb20669e0990cb4", InstanceType.T2_MICRO, 1, 1, KEY_PAIR_NAME, SECURITY_GROUP_NAME);
        System.out.println("Launched successfully the instance!");
        System.out.println("Reserved instances!");
        client.printAllInstances();
        String ebsId = client.createEbsVolume("MyVolume",client.getAvailabilityZone(instanceId), 2, VolumeType.GP2);
        client.addEbsToEc2Instance(instanceId,ebsId, "/dev/sdh");
        System.out.println("Don't forget to terminate the instances!!! if you don't choose terminate");
        System.out.println("Wait for the user command: terminate, stop, relaunch, start, quit");
        Scanner sc = new Scanner(System.in);
        String userChoice;
        do {
            userChoice = sc.nextLine();
            switch (userChoice){
                case "terminate":
                    client.terminateInstance(instanceId);
                    break;
                case "stop":
                    try {
                        client.stopInstance(instanceId, true);
                    } catch (Ec2Exception ex) {
                        System.out.println("Error stopping instance : "  + ex.awsErrorDetails());
                    }
                    break;
                case "start":
                    client.startInstance(instanceId);
                    break;
                case "relaunch":
                    client.terminateInstance(instanceId);
                    instanceId = client.runInstance("MySecondInstance", "ami-00eb20669e0990cb4", InstanceType.T2_MICRO, 1, 1, KEY_PAIR_NAME, SECURITY_GROUP_NAME);
                    break;
            }
        } while(!"quit".equals(userChoice));
    }

    /**
     * Default constructor.
     */
    public Ec2ClientOperations() {
        AwsCredentials credentials = ProfileCredentialsProvider.builder().build().resolveCredentials();
        ec2Client = Ec2Client.builder().credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.US_EAST_1)
                .httpClientBuilder(UrlConnectionHttpClient.builder()).build();
    }

    /**
     * Check if the security group with the specified name exists.
     * @param sgName security group name
     * @return true if the security group exists
     */
    public boolean isSecurityGroupCreated(String sgName) {
        DescribeSecurityGroupsRequest request =
                DescribeSecurityGroupsRequest.builder().groupNames(sgName).build();
        try {
            DescribeSecurityGroupsResponse result = ec2Client.describeSecurityGroups(request);
            return result.securityGroups().stream().map(SecurityGroup::groupName).anyMatch(str -> str.equals(sgName));
        } catch (Ec2Exception e) {
            return false;
        }
    }

    /**
     * Create a security group
     * @param name the name of the security group
     * @param description description of the security group
     * @return true if it created the sg false otherwise
     */
    public boolean createSecurityGroup(String name, String description) {
        if(isSecurityGroupCreated(name)) {
            System.out.println("SG with name=" + name + " is already created we will just add securities");
            return false;
        }
        CreateSecurityGroupRequest sgRequest = CreateSecurityGroupRequest.builder().groupName(name).description(description).build();
        sgResult = ec2Client.createSecurityGroup(sgRequest);
        return true;
    }

    /**
     * add a firewall rule to the security group
     * @param securityGroupName the name of the security group
     * @param ipRangesStr ip ranges for the required protocol
     * @param protocol the protocol
     * @param fromPort inbound port
     * @param toPort outbound port
     * @return  true if it could add the firewall rule
     */
    public boolean addFirewallRule(String securityGroupName, List<String> ipRangesStr, String protocol, int fromPort, int toPort) {

        List<IpRange> ipRanges = new ArrayList<>();
        ipRangesStr.forEach(a -> ipRanges.add(IpRange.builder().cidrIp(a).build()));
        IpPermission ipPermission = IpPermission.builder().ipRanges(ipRanges).ipProtocol(protocol).fromPort(fromPort).toPort(toPort).build();
        AuthorizeSecurityGroupIngressRequest authorizeSecurityGroupIngressRequest = AuthorizeSecurityGroupIngressRequest.builder()
                .groupName(securityGroupName).ipPermissions(ipPermission).build();
        try {
            ec2Client.authorizeSecurityGroupIngress(authorizeSecurityGroupIngressRequest);
            return true;
        } catch (Ec2Exception e) {
            if (e.awsErrorDetails().errorCode().equals("InvalidPermission.Duplicate")) {
                System.out.println("Rule already exist for ipRange " + ipRangesStr + " on protocol " + protocol);
                return true;
            }
            System.out.println(e.getLocalizedMessage());
        }
        return false;
    }

    /**
     * create the key pair for ec2 instances
     * @param keyName the name of the keypair
     * @param  force if true the existing key will be deleted.
     */
    public void createKeyPair(String keyName, boolean force) {
        if (isKeyPairCreated(keyName) && force) {
            System.out.println("The existing keyPair " + keyName + " will be deleted and recreated!");
            DeleteKeyPairRequest request = DeleteKeyPairRequest.builder().keyName(keyName).build();
            try {
                ec2Client.deleteKeyPair(request);
            } catch (Ec2Exception e) {
                e.printStackTrace();
                return;
            }
       } else if (!force) {
            return;
        }
        CreateKeyPairRequest request = CreateKeyPairRequest.builder().keyName(keyName).build();
        keyPair = ec2Client.createKeyPair(request);
    }

    /**
     * This will check if a KeyPair with a name exists.
     * @param keyName the name of the key pair.
     * @return true if the key already exists.
     */
    public boolean isKeyPairCreated(String keyName) {
        DescribeKeyPairsResponse result = ec2Client.describeKeyPairs();
        return result.keyPairs().stream().map(KeyPairInfo::keyName).anyMatch(str -> str.equals(keyName));
    }

    /**
     * This will save the PEM from KeyPair to a file.
     * @param fileName the full path of the file ot save.
     * @throws IOException in case of error
     */
    public void savePEMToFile(String fileName) throws IOException{
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            writer.write(keyPair.keyMaterial());
        }
    }

    /**
     * Run a specific instance
     * @param name  the name of the instance
     * @param osType the type of the image (OS)
     * @param type type of the instance (T2.micro for the Siplilearn account)
     * @param min minimum number of instances
     * @param max maximum number of instances
     * @param keyName the keyPair to connect to instance
     * @param securityGroup the security group assigned to this instance
     * @return string representing the instance id
     */
    public String runInstance(String name, String osType, InstanceType type, int min, int max, String keyName, String securityGroup) {
        RunInstancesRequest request = RunInstancesRequest.builder()
                .imageId(osType).instanceType(type).minCount(min).maxCount(max).keyName(keyName).securityGroups(securityGroup).build();
        RunInstancesResponse result = ec2Client.runInstances(request);
        String instanceId = result.instances().get(0).instanceId();
        CreateTagsRequest tagNameRequest = CreateTagsRequest.builder().resources(instanceId)
                .tags(Tag.builder().key("Name").value(name).build()).build();
        ec2Client.createTags(tagNameRequest);
        return instanceId;
    }

    /**
     * Stop the instance
     * @param instanceId  the id of the instance to be stop
     * @param force true if the instance will be forcefully stoped.
     */
    public void stopInstance(String instanceId, boolean force) throws  Ec2Exception{
        DescribeInstancesRequest describeRequest = DescribeInstancesRequest.builder().instanceIds(instanceId).build();
        DescribeInstancesResponse describeResult = ec2Client.describeInstances(describeRequest);
        if (describeResult.reservations().get(0).instances().get(0).state().code() == 16) {
            StopInstancesRequest stopRequest = StopInstancesRequest.builder().instanceIds(instanceId).force(force).build();
            ec2Client.stopInstances(stopRequest);
        } else {
            System.out.printf("Instance with id %s is not running yet !\n", instanceId);
        }
    }


    /**
     * Start a stopped instance.
     * @param instanceId the id of the instance to be started
     */
    public void startInstance(String instanceId) {
        StartInstancesRequest request = StartInstancesRequest.builder().instanceIds(instanceId).build();
        ec2Client.startInstances(request);
    }

    /**
     * Terminate an instance;
     * @param instanceId the id of the instance to be terminated
     */
    public void terminateInstance(String instanceId) {
        TerminateInstancesRequest request = TerminateInstancesRequest.builder().instanceIds(instanceId).build();
        ec2Client.terminateInstances(request);
    }

    /**
     * print all running instances, this is used for debug mode.
     */
    private void printAllInstances() {
        String nextToken = null;
        do {
            DescribeInstancesRequest request = DescribeInstancesRequest.builder().maxResults(6).nextToken(nextToken).build();
            DescribeInstancesResponse response = ec2Client.describeInstances(request);
            for (Reservation reservation : response.reservations()) {
                for (Instance instance : reservation.instances()) {
                    System.out.printf(
                            "Found reservation with id %s, " +
                                    "AMI %s, " +
                                    "type %s, " +
                                    "state %s " +
                                    "and monitoring state %s",
                            instance.instanceId(),
                            instance.imageId(),
                            instance.instanceType(),
                            instance.state().name(),
                            instance.monitoring().state());
                    System.out.println("");
                }
            }
            nextToken = response.nextToken();
        } while (nextToken != null);
    }

    /**
     * attach ebs to ec2 instance
     * @param instanceId the id of the host instance
     * @param volumeId the volume to be attached
     * @param deviceName the device name /dev/xvdf etc
     */
    public void addEbsToEc2Instance(String instanceId, String volumeId, String deviceName) {
        DescribeInstancesRequest request = DescribeInstancesRequest.builder().instanceIds(instanceId).build();
        DescribeInstancesResponse response;
        do {
            response = ec2Client.describeInstances(request);
        } while(response.reservations().get(0).instances().get(0).state().code() != 16);
        AttachVolumeRequest requestAttach = AttachVolumeRequest.builder().instanceId(instanceId).volumeId(volumeId)
                .device(deviceName).build();
        ec2Client.attachVolume(requestAttach);
    }

    /**
     * Create EBS volume to be attached to a instance.
     * @param name name of the ebs volume
     * @param availabilityZone the availability zone
     * @param size the size of the new volume
     * @param volumeType the type of the volume
     * @return volume Id
     */
    public String createEbsVolume(String name, String availabilityZone, int size, VolumeType volumeType) {
        CreateVolumeRequest volumeRequest = CreateVolumeRequest.builder().availabilityZone(availabilityZone)
                .size(size).volumeType(volumeType).build();
        String volumeId = ec2Client.createVolume(volumeRequest).volumeId();
        CreateTagsRequest tagNameRequest = CreateTagsRequest.builder().resources(volumeId)
                .tags(Tag.builder().key("Name").value(name).build()).build();
        ec2Client.createTags(tagNameRequest);
        return volumeId;
    }

    public String getAvailabilityZone(String instanceId) {
        DescribeInstancesRequest request = DescribeInstancesRequest.builder().instanceIds(instanceId).build();
        DescribeInstancesResponse response = ec2Client.describeInstances(request);
        return response.reservations().get(0).instances().get(0).placement().availabilityZone();
    }
}
