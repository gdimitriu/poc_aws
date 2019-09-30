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

package poc_aws.ec2;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.identitymanagement.model.*;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class EC2Authorization {
    /** ec2 client for amazon instances */
    private AmazonEC2 ec2Client;

    /** key pair for connection to EC2 instances */
    private KeyPair keyPair;

    /** aim client  */
    private AmazonIdentityManagement aimClient;

    private static String EC2_FULL_S3 = "{"+
            "    \"Version\": \"2012-10-17\","+
            "    \"Statement\": ["+
            "        {"+
            "            \"Effect\": \"Allow\","+
            "            \"Action\": \"sts:AssumeRole\","+
            "            \"Principal\": {"+
            "                  \"Service\":[ \"ec2.amazonaws.com\"]"+
            "            }" +
            "        }"+
            "    ]"+
            "}";

    public EC2Authorization(Regions region) {
        AWSCredentials credentials = new ProfileCredentialsProvider().getCredentials();
        ec2Client = AmazonEC2ClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(region).build();
        aimClient = AmazonIdentityManagementClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion("AWS_GLOBAL").build();
    }

    /**
     * get the EC2Client.
     * @return ec2Client.
     */
    public AmazonEC2 getEc2Client() {
        return this.ec2Client;
    }

    /**
     * This will check if a KeyPair with a name exists.
     * @param keyName the name of the key pair.
     * @return true if the key already exists.
     */
    private boolean isKeyPairCreated(String keyName) {
        DescribeKeyPairsResult result = ec2Client.describeKeyPairs();
        return result.getKeyPairs().stream().map(KeyPairInfo::getKeyName).anyMatch(str -> str.equals(keyName));
    }

    /**
     * This will save the PEM from KeyPair to a file.
     * @param fileName the full path of the file ot save.
     * @throws IOException in case of error
     */
    public void savePEMToFile(String fileName) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            writer.write(keyPair.getKeyMaterial());
        }
    }

    /**
     * create the key pair for ec2 instances
     * @param keyName the name of the keypair
     * @param  force if true the existing key will be deleted.
     */
    public void createKeyPair(String keyName, boolean force) {
        if (isKeyPairCreated(keyName) && force) {
            System.out.println("The existing keyPair " + keyName + " will be deleted and recreated!");
            DeleteKeyPairRequest request = new DeleteKeyPairRequest().withKeyName(keyName);
            try {
                ec2Client.deleteKeyPair(request);
            } catch (AmazonEC2Exception e) {
                e.printStackTrace();
                return;
            }
        } else if (!force) {
            return;
        }
        CreateKeyPairRequest request = new CreateKeyPairRequest();
        request.withKeyName(keyName);
        CreateKeyPairResult result = ec2Client.createKeyPair(request);
        keyPair =  result.getKeyPair();
    }

    /**
     * This will create a role for EC2 to have full access to S3.
     * It will create also a intance profile with the same name as the role and with the role attached.
     * @param name name of the role and instance profile
     * @retun the arn of the created role.
     */
    public String createEC2S3FullRoleAndProfile(String name) {
        CreateRoleRequest request = new CreateRoleRequest();
        request.withRoleName(name).withAssumeRolePolicyDocument(EC2_FULL_S3);
        aimClient.createRole(request);
        AttachRolePolicyRequest requestAtachPolicy= new AttachRolePolicyRequest().withRoleName(name).withPolicyArn("arn:aws:iam::aws:policy/AmazonS3FullAccess");
        aimClient.attachRolePolicy(requestAtachPolicy);
        CreateInstanceProfileRequest requestCreateInstanceProfile = new CreateInstanceProfileRequest().withInstanceProfileName(name);
        CreateInstanceProfileResult result = aimClient.createInstanceProfile(requestCreateInstanceProfile);
        AddRoleToInstanceProfileRequest requstAddRoleToInstanceProfile = new AddRoleToInstanceProfileRequest().withInstanceProfileName(name).withRoleName(name);
        aimClient.addRoleToInstanceProfile(requstAddRoleToInstanceProfile);
        return result.getInstanceProfile().getArn();
    }
}
