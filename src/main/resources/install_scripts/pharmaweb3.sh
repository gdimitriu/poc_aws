#!/bin/bash
exec > /tmp/start.log  2>&1
sudo yum update -y
sudo yum install httpd -y
sudo chkconfig httpd on
sudo /etc/init.d/httpd start
aws s3 cp s3://gabrieldimitriupharmaweb/pharma_webservers/web3.zip web.zip
sudo unzip web.zip -d /var/www/html/
rm web.zip
aws s3 cp s3://gabrieldimitriupharmaweb/pharma_webservers/welcome.conf welcome.conf
sudo cp welcome.conf /etc/httpd/conf.d/
sudo /etc/init.d/httpd restart