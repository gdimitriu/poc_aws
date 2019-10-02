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

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.model.InstanceType;
import poc_aws.ec2.EC2Infrastructure;
import poc_aws.ec2.EC2Authorization;
import poc_aws.ec2.EC2Instances;
import poc_aws.s3.S3Infrastructure;

import java.io.IOException;
import java.util.Arrays;

public class PharmaEc2Servers {

    private EC2Infrastructure ec2Infrastructure;
    private EC2Authorization ec2Authorization;
    private EC2Instances ec2Instances;
    private S3Infrastructure s3Infrastructure;

    /** key-pair to connect to EC2 instances */
    private static final String KEY_PAIR_NAME = "javaAutoKeyPair";

    /** security group for web servers */
    private static final String SECURITY_GROUP_WEBSERVER_NAME= "WebServerSecurityGroupLinux";

    /** security group for web servers description */
    private static final String SECURITY_GROUP_WEBSERVER_DESCRIPTION = "Web Server Security Group Linux";

    /** security group for DB servers */
    private static final String SECURITY_GROUP_DBSERVER_NAME= "DBServerSecurityGroupLinux";

    /** security group for DB servers description */
    private static final String SECURITY_GROUP_DBSERVER_DESCRIPTION = "DB Server Security Group Linux";

    private static final String LOAD_BALANCER_NAME = "PharmaLB";

    private static final String VPC_NAME = "PharmaVPC";

    private static final String PUBLIC_ROUTE_TABLE_NAME = "PubicPharmaRT";
    private static final String PRIVATE_ROUTE_TABLE_NAME = "PrivatePharmaRT";

    private static final String INTERNET_GATEWAY_NAME = "IgwPharma";
    private static final String NAT_GATEWAY_NAME = "NatPharma";

    private static final String AZ_1 = "us-east-1a";
    private static final String AZ_2 = "us-east-1b";

    private static final String PHARMA_SUBNET_1 = "10.0.1.0/24";
    private static final String PHARMA_SUBNET_1_NAME = "1aPublicPharma";
    private static final String PHARMA_SUBNET_2 = "10.0.2.0/24";
    private static final String PHARMA_SUBNET_2_NAME = "1bPublicPharma";
    private static final String PHARMA_SUBNET_3 = "10.0.3.0/24";
    private static final String PHARMA_SUBNET_3_NAME = "1aPrivatePharma";
    private static final String PHARMA_SUBNET_4 = "10.0.4.0/24";
    private static final String PHARMA_SUBNET_4_NAME = "1bPrivatePharma";

    private static final String MACHINE_IMAGE = "ami-00eb20669e0990cb4";
    /** name of the bucket where the website sources will reside */
    private static final String ROOT_BUCKET_DELIVERY_NAME = "gabrieldimitriupharmaweb";

    /** Install script for the first web server */
    private static final String INSTALL_SCRIPT_1="#!/bin/bash\n" +
            "exec > /tmp/start.log  2>&1\n" +
            "sudo yum update -y\n" +
            "sudo yum install httpd -y\n" +
            "sudo chkconfig httpd on\n" +
            "cd /home/ec2-user\n" +
            "aws s3 cp s3://" + ROOT_BUCKET_DELIVERY_NAME + "/pharma_webservers/web1.zip web.zip\n" +
            "sudo unzip web.zip -d /var/www/html/\n" +
            "rm web.zip\n" +
            "aws s3 cp s3://" + ROOT_BUCKET_DELIVERY_NAME + "/pharma_webservers/welcome.conf welcome.conf\n" +
            "sudo cp welcome.conf /etc/httpd/conf.d/" +
            "rm welcome.conf\n" +
            "sudo /etc/init.d/httpd start";

    /** Install script for the second web server */
    private static final String INSTALL_SCRIPT_2="#!/bin/bash\n" +
            "exec > /tmp/start.log  2>&1\n" +
            "sudo yum update -y\n" +
            "sudo yum install httpd -y\n" +
            "sudo chkconfig httpd on\n" +
            "cd /home/ec2-user\n" +
            "aws s3 cp s3://" + ROOT_BUCKET_DELIVERY_NAME + "/pharma_webservers/web2.zip web.zip\n" +
            "sudo unzip web.zip -d /var/www/html/\n" +
            "rm web.zip\n" +
            "aws s3 cp s3://" + ROOT_BUCKET_DELIVERY_NAME + "/pharma_webservers/welcome.conf welcome.conf\n" +
            "sudo cp welcome.conf /etc/httpd/conf.d/" +
            "rm welcome.conf\n" +
            "sudo /etc/init.d/httpd start";

    private static final String EC2_FULL_ACCESS_TO_S3 = "EC2_WITH_S3";
    private PharmaEc2Servers() {
        ec2Authorization = new EC2Authorization(Regions.US_EAST_1);
        //create the role and instance profile for access to S3.
        ec2Authorization.createEC2S3FullRoleAndProfile(EC2_FULL_ACCESS_TO_S3);
        //upload the webservers on S3.
        s3Infrastructure = new S3Infrastructure(Regions.US_EAST_1);
        s3Infrastructure.uploadNewFileToBucket(ROOT_BUCKET_DELIVERY_NAME, "pharma_webservers/pharma_web1.zip", "pharma_webservers/web1.zip");
        s3Infrastructure.uploadNewFileToBucket(ROOT_BUCKET_DELIVERY_NAME, "pharma_webservers/pharma_web2.zip", "pharma_webservers/web2.zip");
        s3Infrastructure.uploadNewFileToBucket(ROOT_BUCKET_DELIVERY_NAME, "pharma_webservers/welcome.conf", "pharma_webservers/welcome.conf");
        ec2Authorization.createKeyPair(KEY_PAIR_NAME, true);
        //sleep is needed to the role to be available to all AZ.
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {

        }

        try {
            ec2Authorization.savePEMToFile("d:\\" + KEY_PAIR_NAME + ".pem");
        } catch (IOException e) {
            e.printStackTrace();
        }

        //create the network infrastructure.
        ec2Infrastructure = new EC2Infrastructure(ec2Authorization.getEc2Client());
        ec2Instances = new EC2Instances((ec2Authorization.getEc2Client()));
        String vpcId = ec2Infrastructure.createVpc(VPC_NAME, "10.0.0.0/16").getVpcId();
        ec2Infrastructure.createSecurityGroup(SECURITY_GROUP_DBSERVER_NAME,SECURITY_GROUP_DBSERVER_DESCRIPTION, vpcId);
        ec2Infrastructure.addFirewallRule(vpcId, SECURITY_GROUP_DBSERVER_NAME, Arrays.asList(new String[]{"0.0.0.0/0"}), "tcp", 22, 22);
        ec2Infrastructure.createSecurityGroup(SECURITY_GROUP_WEBSERVER_NAME,SECURITY_GROUP_WEBSERVER_DESCRIPTION, vpcId);
        ec2Infrastructure.addFirewallRule(vpcId, SECURITY_GROUP_WEBSERVER_NAME, Arrays.asList(new String[]{"0.0.0.0/0"}), "tcp", 22, 22);
        ec2Infrastructure.addFirewallRule(vpcId, SECURITY_GROUP_WEBSERVER_NAME, Arrays.asList(new String[]{"0.0.0.0/0"}), "tcp", 80, 80);
        ec2Infrastructure.addSubnet(vpcId,PHARMA_SUBNET_1, AZ_1 , PHARMA_SUBNET_1_NAME);
        ec2Infrastructure.addSubnet(vpcId,PHARMA_SUBNET_2, AZ_2, PHARMA_SUBNET_2_NAME );
        ec2Infrastructure.addSubnet(vpcId,PHARMA_SUBNET_3, AZ_1 , PHARMA_SUBNET_3_NAME);
        ec2Infrastructure.addSubnet(vpcId,PHARMA_SUBNET_4, AZ_2 , PHARMA_SUBNET_4_NAME);
        //create web machines
        String w1Id = ec2Instances.runInstance(MACHINE_IMAGE, InstanceType.T2Micro, 1, 1, KEY_PAIR_NAME, ec2Infrastructure.getSecurityGroupId(vpcId, SECURITY_GROUP_WEBSERVER_NAME),
                ec2Infrastructure.getSubnetId(PHARMA_SUBNET_1), INSTALL_SCRIPT_1, "WebInstance1",EC2_FULL_ACCESS_TO_S3);
        String w2Id = ec2Instances.runInstance(MACHINE_IMAGE, InstanceType.T2Micro, 1, 1, KEY_PAIR_NAME, ec2Infrastructure.getSecurityGroupId(vpcId, SECURITY_GROUP_WEBSERVER_NAME),
                ec2Infrastructure.getSubnetId(PHARMA_SUBNET_2), INSTALL_SCRIPT_2, "WebInstance2", EC2_FULL_ACCESS_TO_S3);
        //create db machines
        String i1Id = ec2Instances.runInstance(MACHINE_IMAGE, InstanceType.T2Micro, 1, 1, KEY_PAIR_NAME, ec2Infrastructure.getSecurityGroupId(vpcId, SECURITY_GROUP_DBSERVER_NAME),
                ec2Infrastructure.getSubnetId(PHARMA_SUBNET_3), null, "DBInstance1", EC2_FULL_ACCESS_TO_S3);
        String i2Id = ec2Instances.runInstance(MACHINE_IMAGE, InstanceType.T2Micro, 1, 1, KEY_PAIR_NAME, ec2Infrastructure.getSecurityGroupId(vpcId, SECURITY_GROUP_DBSERVER_NAME),
                ec2Infrastructure.getSubnetId(PHARMA_SUBNET_4), null, "DBInstance2", EC2_FULL_ACCESS_TO_S3);

        String igId = ec2Infrastructure.createInternetGateway(INTERNET_GATEWAY_NAME, vpcId);
        ec2Infrastructure.addTagNameToNewCreatedRouteTable(PUBLIC_ROUTE_TABLE_NAME , vpcId);
        ec2Infrastructure.addRouteToRouteTableToInternetGatewayId("0.0.0.0/0", igId, PUBLIC_ROUTE_TABLE_NAME);
        ec2Infrastructure.assignSubnetToRouteTable(ec2Infrastructure.getSubnetId(PHARMA_SUBNET_1), PUBLIC_ROUTE_TABLE_NAME);
        ec2Infrastructure.assignSubnetToRouteTable(ec2Infrastructure.getSubnetId(PHARMA_SUBNET_2), PUBLIC_ROUTE_TABLE_NAME);

        //create the NAT
        String elasticIpId = ec2Infrastructure.createElasticIpAddressOnVpc();

        String natGateway1 = ec2Infrastructure.createNatGateway(NAT_GATEWAY_NAME, ec2Infrastructure.getSubnetId(PHARMA_SUBNET_3), elasticIpId);
        ec2Infrastructure.createRouteTable(PRIVATE_ROUTE_TABLE_NAME, vpcId);
        ec2Infrastructure.addRouteToRouteTableToNatGatewayId("0.0.0.0/0", natGateway1, PRIVATE_ROUTE_TABLE_NAME);
        ec2Infrastructure.assignSubnetToRouteTable(ec2Infrastructure.getSubnetId(PHARMA_SUBNET_3), PRIVATE_ROUTE_TABLE_NAME);
        ec2Infrastructure.assignSubnetToRouteTable(ec2Infrastructure.getSubnetId(PHARMA_SUBNET_4), PRIVATE_ROUTE_TABLE_NAME);
        //create the load balancer
        ec2Infrastructure.addListnerToLoadBalancer(LOAD_BALANCER_NAME, "HTTP", 80, "HTTP", 80);
        ec2Infrastructure.createLoadBalancer(LOAD_BALANCER_NAME, true,  Arrays.asList(ec2Infrastructure.getSubnetId(PHARMA_SUBNET_1),
                ec2Infrastructure.getSubnetId(PHARMA_SUBNET_2)), ec2Infrastructure.getSecurityGroupId(vpcId, SECURITY_GROUP_WEBSERVER_NAME));
        ec2Infrastructure.addInstacesToLB(LOAD_BALANCER_NAME,Arrays.asList(w1Id, w2Id));


    }


    public static void main(String...args) {
        PharmaEc2Servers pharma = new PharmaEc2Servers();
    }
}
