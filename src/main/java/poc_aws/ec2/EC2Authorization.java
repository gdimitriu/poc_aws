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

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.*;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class EC2Authorization {
    /** ec2 client for amazon instances */
    private Ec2Client ec2Client;

    /** key pair for connection to EC2 instances */
    private CreateKeyPairResponse keyPair;

    /** iam client  */
    private IamClient iamClient;

    /** the client for the Amazon storage */
    private S3Client s3client;

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

    public EC2Authorization(Region region) {
        AwsCredentials credentials = ProfileCredentialsProvider.builder().build().resolveCredentials();
        ec2Client = Ec2Client.builder().credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(region).httpClientBuilder(UrlConnectionHttpClient.builder()).build();
        iamClient = IamClient.builder().credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.AWS_GLOBAL).httpClientBuilder(UrlConnectionHttpClient.builder()).build();
        s3client = S3Client.builder().credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(region).httpClientBuilder(UrlConnectionHttpClient.builder()).build();
    }

    /**
     * get the EC2Client.
     * @return ec2Client.
     */
    public Ec2Client getEc2Client() {
        return this.ec2Client;
    }

    /**
     * get the S3Client
     * @return s3Client
     */
    public S3Client getS3Client() {
        return this.s3client;
    }

    /**
     * This will check if a KeyPair with a name exists.
     * @param keyName the name of the key pair.
     * @return true if the key already exists.
     */
    private boolean isKeyPairCreated(String keyName) {
        DescribeKeyPairsResponse result = ec2Client.describeKeyPairs();
        return result.keyPairs().stream().map(KeyPairInfo::keyName).anyMatch(str -> str.equals(keyName));
    }

    /**
     * This will save the PEM from KeyPair to a file.
     * @param fileName the full path of the file ot save.
     * @throws IOException in case of error
     */
    public void savePEMToFile(String fileName) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            writer.write(keyPair.keyMaterial());
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
     * This will create a role for EC2 to have full access to S3.
     * It will create also a intance profile with the same name as the role and with the role attached.
     * @param name name of the role and instance profile
     * @retun the arn of the created role.
     */
    public String createEC2S3FullRoleAndProfile(String name) {
        CreateRoleRequest request = CreateRoleRequest.builder().roleName(name).assumeRolePolicyDocument(EC2_FULL_S3).build();
        iamClient.createRole(request);
        AttachRolePolicyRequest requestAtachPolicy= AttachRolePolicyRequest.builder().roleName(name).policyArn("arn:aws:iam::aws:policy/AmazonS3FullAccess").build();
        iamClient.attachRolePolicy(requestAtachPolicy);
        CreateInstanceProfileRequest requestCreateInstanceProfile = CreateInstanceProfileRequest.builder().instanceProfileName(name).build();
        CreateInstanceProfileResponse result = iamClient.createInstanceProfile(requestCreateInstanceProfile);
        AddRoleToInstanceProfileRequest requstAddRoleToInstanceProfile = AddRoleToInstanceProfileRequest.builder().instanceProfileName(name).roleName(name).build();
        iamClient.addRoleToInstanceProfile(requstAddRoleToInstanceProfile);
        return result.instanceProfile().arn();
    }
}
