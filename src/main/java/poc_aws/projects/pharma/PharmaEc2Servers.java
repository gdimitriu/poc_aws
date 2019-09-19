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

import java.util.Arrays;

public class PharmaEc2Servers {

    private EC2Infrastructure ec2Infrastructure;
    private EC2Authorization ec2Authorization;
    private EC2Instances ec2Instances;

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

    private static final String AZ_1 = "us-east-1a";
    private static final String AZ_2 = "us-east-1b";

    private static final String PHARMA_SUBNET_1 = "10.50.1.0/24";
    private static final String PHARMA_SUBNET_2 = "10.50.2.0/24";
    private static final String PHARMA_SUBNET_3 = "10.50.3.0/24";
    private static final String PHARMA_SUBNET_4 = "10.50.4.0/24";

    private static final String MACHINE_IMAGE = "ami-00eb20669e0990cb4";

    private PharmaEc2Servers() {
        ec2Authorization = new EC2Authorization(Regions.US_EAST_1);
        ec2Authorization.createKeyPair(KEY_PAIR_NAME, true);
        ec2Infrastructure = new EC2Infrastructure(ec2Authorization.getEc2Client());
        ec2Instances = new EC2Instances((ec2Authorization.getEc2Client()));
        String vpcId = ec2Infrastructure.createVpc("10.50.0.0/16").getVpcId();
        ec2Infrastructure.createSecurityGroup(SECURITY_GROUP_DBSERVER_NAME,SECURITY_GROUP_DBSERVER_DESCRIPTION, vpcId);
        ec2Infrastructure.addFirewallRule(SECURITY_GROUP_DBSERVER_NAME, Arrays.asList(new String[]{"0.0.0.0/0"}), "tcp", 22, 22);
        ec2Infrastructure.createSecurityGroup(SECURITY_GROUP_WEBSERVER_NAME,SECURITY_GROUP_WEBSERVER_DESCRIPTION, vpcId);
        ec2Infrastructure.addFirewallRule(SECURITY_GROUP_WEBSERVER_NAME, Arrays.asList(new String[]{"0.0.0.0/0"}), "tcp", 22, 22);
        ec2Infrastructure.addFirewallRule(SECURITY_GROUP_WEBSERVER_NAME, Arrays.asList(new String[]{"0.0.0.0/0"}), "tcp", 80, 80);
        ec2Infrastructure.addSubnet(vpcId,PHARMA_SUBNET_1, AZ_1 );
        ec2Infrastructure.addSubnet(vpcId,PHARMA_SUBNET_2, AZ_2 );
        ec2Infrastructure.addSubnet(vpcId,PHARMA_SUBNET_3, AZ_1 );
        ec2Infrastructure.addSubnet(vpcId,PHARMA_SUBNET_4, AZ_2 );
        //create web machines
        String w1Id = ec2Instances.runInstance(MACHINE_IMAGE, InstanceType.T2Micro, 1, 1, KEY_PAIR_NAME, SECURITY_GROUP_WEBSERVER_NAME, vpcId, ec2Infrastructure.getSubnetId(PHARMA_SUBNET_1));
        String w2Id = ec2Instances.runInstance(MACHINE_IMAGE, InstanceType.T2Micro, 1, 1, KEY_PAIR_NAME, SECURITY_GROUP_WEBSERVER_NAME, vpcId, ec2Infrastructure.getSubnetId(PHARMA_SUBNET_2));
        //create db machines
        String i1Id = ec2Instances.runInstance(MACHINE_IMAGE, InstanceType.T2Micro, 1, 1, KEY_PAIR_NAME, SECURITY_GROUP_DBSERVER_NAME, vpcId, ec2Infrastructure.getSubnetId(PHARMA_SUBNET_3));
        String i2Id = ec2Instances.runInstance(MACHINE_IMAGE, InstanceType.T2Micro, 1, 1, KEY_PAIR_NAME, SECURITY_GROUP_DBSERVER_NAME, vpcId, ec2Infrastructure.getSubnetId(PHARMA_SUBNET_4));

        ec2Infrastructure.createLoadBalancer(LOAD_BALANCER_NAME, Arrays.asList(AZ_1, AZ_2), Arrays.asList(ec2Infrastructure.getSubnetId(PHARMA_SUBNET_1),ec2Infrastructure.getSubnetId(PHARMA_SUBNET_2)), SECURITY_GROUP_WEBSERVER_NAME);
        ec2Infrastructure.addListnerToLoadBalancer(LOAD_BALANCER_NAME, "HTTP", 80, "HTTP", 80).addInstacesToLB(LOAD_BALANCER_NAME,Arrays.asList(w1Id, w2Id));

    }


    public static void main(String...args) {
        PharmaEc2Servers pharma = new PharmaEc2Servers();
    }
}
