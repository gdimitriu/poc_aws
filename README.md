# poc_aws
aws examples and pocs

The amazon key-pair from the csv file should be saved into the c:\users\user\.aws\credentials with the following format:

[default]

aws_access_key_id= key_id from csv

aws_secret_access_key= access_key from cvs


Into the projects/pharma there is the project to create a pharmaceutical site:
- it will create authomatically key-pair and save on D:\keypair-name.pem to be used for ssh
- all except load balancer are into a private virtual cloud
- the webserver site are distributed on S3
- two web servers into two availability zones which will take the site from S3
- load balancer in front of them
- two databases (not populated yet)
- each database instance has a NAT assigned to a elastic IP address
- Internet gateway for the web servers to be available from outside

Into the poc_tests are diffent small test for the amazon services:
- ec2 : creation, start, stop, adding ebs(not yet functional).
- aim to create a role
- s3 to create a web site.
