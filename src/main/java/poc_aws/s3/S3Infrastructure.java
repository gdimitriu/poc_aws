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
package poc_aws.s3;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.File;
import java.net.URL;
import java.util.List;

public class S3Infrastructure {

    /** the client for the Amazon storage */
    private S3Client s3client;

    public S3Infrastructure(Region region) {
        AwsCredentials credentials = ProfileCredentialsProvider.builder().build().resolveCredentials();
        s3client = S3Client.builder().credentialsProvider(StaticCredentialsProvider.create(credentials)).region(region)
                .httpClientBuilder(UrlConnectionHttpClient.builder()).build();
    }

    public S3Infrastructure(S3Client client) {
        this.s3client = client;
    }

    /**
     * Create a bucket with the specific name into the region.
     * @param bucketName the name of the bucket
     */
    public void createBucket(String bucketName) {
        if (bucketExists(bucketName)) {
            System.out.println("Bucket with name " + bucketName + " already exist!");
            return;
        }
        s3client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
    }

    /**
     * get the file from resource
     * @param resourceFile  name of the resource
     * @return the file
     */
    private File getFileFromResource(String resourceFile) {
        URL url = this.getClass().getClassLoader().getResource(resourceFile);
        if (url != null) {
            return new File(url.getFile());
        }
        return null;
    }

    /**
     * upload new file to Bucket.
     * If the bucket does not exist create it.
     * If the file exist print message.
     * @param bucketName the name of the bucket
     * @param fileToUpload the file to upload
     * @param fileOnS3Name the name of the file on s3
     * @return true if file is uploaded, false if exists
     */
    public boolean uploadNewFileToBucket(String bucketName, String fileToUpload, String fileOnS3Name) {
        File file = getFileFromResource(fileToUpload);
        if (file == null) {
            return false;
        }
        createBucket(bucketName);
        if (!objectExists(bucketName, file.getName())) {
            s3client.putObject(PutObjectRequest.builder().bucket(bucketName).key(fileOnS3Name).build(), file.toPath());
            return true;
        } else {
            System.out.println("Object " + fileToUpload + " already exists!");
            return false;
        }
    }

    private boolean objectExists(String bucketName, String fileName) {
        List<S3Object> objects = s3client.listObjects(ListObjectsRequest.builder().bucket(bucketName).build()).contents();
        for (S3Object object : objects) {
            if (object.key().equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    private boolean bucketExists(String bucketName) {
        List<Bucket> buckets = s3client.listBuckets(ListBucketsRequest.builder().build()).buckets();
        for (Bucket bucket : buckets) {
            if (bucket.name().equals(bucketName)) {
                return true;
            }
        }
        return false;
    }
}
