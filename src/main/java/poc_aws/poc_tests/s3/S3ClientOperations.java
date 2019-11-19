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

package poc_aws.poc_tests.s3;

import poc_aws.utils.auth.Policy;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("access-can-be-private")
public class S3ClientOperations {

    private S3Client s3client;
    /**
     * bucket should be unique across entire S3
     */
    private static final String BUCKET_PREFIX = "gabrieldimitriu";
    private static final String BUCKET_LOAD = BUCKET_PREFIX + "load";

    public static void main(String... args) {
        System.out.println("Create buckets in s3");
        S3ClientOperations client = new S3ClientOperations();
        client.createBucket(BUCKET_PREFIX + "test");
        client.createBucket(BUCKET_PREFIX + "test1");
        List<Bucket> buckets = client.getAllBuckets();
        buckets.stream().map(Bucket::name).forEach(System.out::println);

        System.out.println("Delete the not empty bucket at second run of the test");
        client.forceDeleteNotEmptyBucket(BUCKET_LOAD);
        System.out.println("Now delete all buckets");
        client.removeBuckets(buckets).forEach((key, value) -> System.out.println("bucket=" + key.name() + " error = " + value.getLocalizedMessage()));

        System.out.println("Create bucket and upload files");
        client.uploadFileToBucket(BUCKET_LOAD, "poc_tests/testDocument.txt", "testDocument1.txt");
        client.uploadFileToBucket(BUCKET_LOAD, "poc_tests/testDocument.txt", "testDocument2.txt");
        client.uploadFileToBucket(BUCKET_LOAD, "poc_tests/testDocument.txt", "testDocument3.txt");

        System.out.println("Download and print objects");
        List<InputStream> inputStreams = client.getAllObjectFromBucket(BUCKET_LOAD);
        inputStreams.stream().map(is -> new BufferedReader(new InputStreamReader(is)).lines().collect(Collectors.joining("\n"))).forEach(System.out::println);
        System.out.println("Make the populated bucket pubic");
        client.setPublicReadPolicy(BUCKET_LOAD);
    }

    public S3ClientOperations() {
        AwsCredentials credentials = ProfileCredentialsProvider.builder().build().resolveCredentials();
        s3client = S3Client.builder().credentialsProvider(StaticCredentialsProvider.create(credentials)).region(Region.US_EAST_1)
                .httpClientBuilder(UrlConnectionHttpClient.builder()).build();
    }

    public void createBucket(String bucketName) {
        if(!bucketExists(bucketName)) {
            s3client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        }
    }

    /**
     * get all the buckets of the user and region
     * @return list of buckets
     */
    public List<Bucket> getAllBuckets() {
        return s3client.listBuckets().buckets();
    }

    /**
     * This will remove the buckets, the removed bocket is removed also from the list
     *
     * @param toRemove list of buckets to be removed
     * @return map of buckets with exceptions
     */
    public Map<Bucket, Exception> removeBuckets(List<Bucket> toRemove) {
        Map<Bucket, Exception> removeError = new HashMap<>();
        Iterator iter = toRemove.iterator();
        while (iter.hasNext()) {
            Bucket bucket = (Bucket) iter.next();
            try {
                forceDeleteNotEmptyBucket(bucket.name());
            } catch (S3Exception e) {
                removeError.put(bucket, e);
            }
        }
        return removeError;
    }

    /**
     * get the file from resource
     *
     * @param resourceFile name of the resource
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
     * upload file to Bucket.
     * If the bucket does not exist create it.
     * If the file exist print message.
     *
     * @param bucketName   the name of the bucket
     * @param fileToUpload the file to upload
     * @param fileOnS3Name the name of the file on s3
     * @return true if file is uploaded, false if exists
     */
    public boolean uploadFileToBucket(String bucketName, String fileToUpload, String fileOnS3Name) {
        File file = getFileFromResource(fileToUpload);
        if (file == null) {
            return false;
        }
        if (!bucketExists(bucketName)) {
            createBucket(bucketName);
        }
        if (!objectExists(bucketName, fileOnS3Name)) {
            s3client.putObject(PutObjectRequest.builder().bucket(bucketName).key(fileOnS3Name).build(), file.toPath());
        }
        return true;
    }

    /**
     * check if the object (file) exist into a bucket
     * @param bucketName the name of the bucket
     * @param fileName the name of the file
     * @return true if the file exists otherwise false
     */
    private boolean objectExists(String bucketName, String fileName) {
        List<S3Object> objects = s3client.listObjects(ListObjectsRequest.builder().bucket(bucketName).build()).contents();
        for (S3Object object : objects) {
            if (object.key().equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * check if the bucket exists
     * @param bucketName the name of the bucket
     * @return true if the bucket exists false otherwise
     */
    private boolean bucketExists(String bucketName) {
        List<Bucket> buckets = s3client.listBuckets(ListBucketsRequest.builder().build()).buckets();
        for (Bucket bucket : buckets) {
            if (bucket.name().equals(bucketName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get all the files (objects) from a bucket
     * @param bucketName the name of the bucket
     * @return the list of input streams
     */
    public List<InputStream> getAllObjectFromBucket(String bucketName) {
        List<InputStream> objects = new ArrayList<>();
        s3client.listObjects(ListObjectsRequest.builder().bucket(bucketName).build()).contents()
                .forEach(ob -> objects.add(s3client.getObject(GetObjectRequest.builder().bucket(bucketName).key(ob.key()).build(), ResponseTransformer.toInputStream())));
        return objects;
    }

    /**
     * force delete the bucket. s3 does not have the notion of folder is just a prefix in front of the file.
     * so to delete all elements from a bucket you have to delete all objects.
     *
     * @param bucketName the name of the bucket
     */
    private void forceDeleteNotEmptyBucket(String bucketName) {
        if (bucketExists(bucketName)) {
            s3client.listObjects(ListObjectsRequest.builder().bucket(bucketName).build()).contents()
                    .forEach(ob -> s3client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(ob.key()).build()));
            s3client.deleteBucket(DeleteBucketRequest.builder().bucket(bucketName).build());
        }
    }

    public void setPublicReadPolicy(String bucketName) {
        String readAllPolicyJson = new Policy("2012-10-17")
                .withEffect("Allow").withAction("s3:GetObject")
                .withResource("arn:aws:s3:::" + bucketName + "/*")
                .withPrincipal("AWS","*").toJson();
        s3client.putBucketPolicy(PutBucketPolicyRequest.builder().bucket(bucketName).policy(readAllPolicyJson).build());
    }
}
