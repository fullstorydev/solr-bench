# Solr benchmarking infrastructure
This directory contains Terraform configurations to create Solr Inrastructure for benchmarking.

## Overview
The terraform configurations are used to create a Zookeeper node and desired number of Solr nodes in a cluster.

## Infrastructure Diagram
![alt text][infrastructure]

[infrastructure]: assets/images/TerraformInfrastructure_v1.0.png "Solr with Zookeeper on Google Cloud Platform"

## Supported Terraform versions
Terraform 0.12.6+

## How it works

### Pre-requisites
The configurations assume that
- Project has already been set up.
- Network and firewall is already created.
- Make sure that the network's firewall(s) allow communication between Solr nodes and Zookeeper node on port 2181 and that Solr nodes are open on 8983.
- SSH keys are created so that the instances could be connected over SSH using those keys. The path to the SSH keys should be supplied to the configurations using the `private_key_path` and `public_key_path` parameters.
- The user or the machine running the terraform has permissions to create Google Compute instances

### Resources created by the configuration
The following resources would be created on running the terraform configurations:
1. A Zookeeper instance
1. Solr nodes - the number of nodes can be configured through the parameter `solr_node_count`
1. A user would be created for each compute instance. The name of this user should be supplied by the variable `user`. Path to the SSH key-pair should also be supplied via variables `private_key_path` and `public_key_path`.

### Customization
The configurations can be customized using the various parameters described in the section __Inputs__.  
Only `project_id` is required parameters that must be supplied at the time of execution. However, it is advisable to go through the `variables.tf` file to understand the default values of the other parameters and override them if necessary.  
The inputs can be provided through a JSON file or a tfvars file with required overrides. A sample input file can be viewed in the `example` directory.

### Execution
Before executing the configurations, make sure that the SSH key-pair is present in the location specified by `private_key_path` and `public_key_path`.  

From the `terraform` directory, initialize the configurations through the `terraform init` command.
```
terraform init
```
You should create a plan and review the items that would be created/modified/destroyed by the configurations.
```
terraform plan -out=plan.terraform -no-color | tee plan.terraform.review.txt
```
Once satisfied with the plan, execute the configurations through `terraform apply` command.
```
terraform apply plan.terraform
```

## Input parameters
| Name | Description  | Type| Default | Required |
|--|--|-- |--|--|
| project_id | The  id of the project under which this should run  | string | - | yes |
| region | The  name  of  the  google cloud  region  | string | us-central1 | no |
| zone | The GCP zone | string | us-central1-a | no |
| zookeeper_instance_name | The name of the zookeeper instance. It would be postfixed with -1 | string | zookeeper | no |
| zookeeper_machine_type | The machine size of the zookeeper instance | string | n1-standard-2 | no |
| zookeeper_boot_image | The image from which to initialize the boot disk | string | centos-8-v20191210 | no |
| zookeeper_disk_type | The GCE disk type | string | pd-standard | no |
| zookeeper_device_name | Name with which attached disk will be accessible. On the instance, this device will be /dev/disk/by-id/google-{{device_name}} | string | zookeeper-device | no |
| solr_node_count | The number of Solr nodes to create | number | 3 | no |
| solr_instance_name | The name of the Solr instance. It would be postfixed with -n, where n is the machine number | string | solr | no |
| solr_machine_type | The machine size of the Solr nodes | string | n1-standard-2 | no |
| solr_boot_image | The image from which to initialize the boot disk | string | centos-8-v20191210 | no |
| solr_disk_type | The GCE disk type for Solr instances | string | pd-standard | no |
| solr_device_name | Name with which attached disk will be accessible. On the instance, this device will be /dev/disk/by-id/google-{{device_name}} | string | solr-device | no |
| tags | A list of tags to attach to the instance. Make sure that they match the tags for the firewall rules set up for the instances | list of strings | ["benchmarking", "solr"] | no |
| machine_labels | A map of key/value label pairs to assign to the instance | map | { product = "solr", purpose = "benchmarking", automation = "true"} | no |
| network_name | The name of the network in which the VMs would be created | string | default | no |
| private_key_path | Path to private SSH key. Make sure that the private key exists in this location | string | ../../terraform_ssh_keys/id_rsa | no |
| public_key_path | Path to public SSH key. Make sure that the public key exists in this location | string | ../../terraform_ssh_keys/id_rsa.pub | no |
| user | The user with which you can ssh into the VM. This user would be created if it does not exists | string | solruser | no |

## Outputs
The details of the Zookeeper node and the Solr nodes are returned as outputs.
These details can be accessed through the `terraform output` command.

### Example #1
In order to get the entire details of all the outputs, simply the `terraform output` command could be used.
```
terraform output
```

### Example #2
To fetch a specific property of a resource, the `-json` flag can be used along with `jq`.  
For example, to fetch the `zone` of the zookeeper instance:
```
terraform output -json zookeeper_details | jq '.[0].zone'
```
#### Result
```
"us-central1-a"
```
## Backend
The configurations does not specify a backend for the Terraform state file. Hence, the state file would be stored locally. 
If needed, please modify the script in `terraform.tf` to add the backend.
For example, to use GCS as backend:
```
terraform {
  backend "gcs" {
    bucket      = "state-file-bucket-name"
    credentials = "/path/to/credentials/file"
    prefix      = "state/solr-benchmarking.tfstate"
  }
  required_version = "~>0.12.0"
  required_providers {
    google = "~> 3.20.0"
  }
}
```