variable "project_id" {
  description = "The project id"
}

variable "region" {
  description = "The GCP region"
  default     = "us-central1"
}

variable "zone" {
  description = "The GCP zone"
  default     = "us-central1-a"
}

variable "zookeeper_instance_name" {
  description = "The name of the zookeeper instance. It would be postfixed with -1"
  default     = "zookeeper"
}

variable "zookeeper_machine_type" {
  description = "The machine type to create"
  default     = "n1-standard-2"
}

variable "zookeeper_boot_image" {
  description = "The image from which to initialize the boot disk."
  default     = "centos-8-v20191210"
}

variable "zookeeper_disk_type" {
  description = "The GCE disk type"
  default     = "pd-standard"
}

variable "zookeeper_device_name" {
  description = "Name with which attached disk will be accessible. On the instance, this device will be /dev/disk/by-id/google-{{device_name}}"
  default     = "zookeeper-device"
}

variable "solr_node_count" {
  description = "The number of Solr nodes to create"
  default     = 3
}

variable "solr_instance_name" {
  description = "The name of the solr instance. It would be postfixed with -n, where n is the machine number"
  default     = "solr"
}

variable "solr_machine_type" {
  description = "The machine type to create"
  default     = "n1-standard-2"
}

variable "solr_boot_image" {
  description = "The image from which to initialize the boot disk."
  default     = "centos-8-v20191210"
}

variable "solr_disk_type" {
  description = "The GCE disk type"
  default     = "pd-standard"
}

variable "solr_device_name" {
  description = "Name with which attached disk will be accessible. On the instance, this device will be /dev/disk/by-id/google-{{device_name}}"
  default     = "solr-device"
}

variable "tags" {
  description = "A list of tags to attach to the instance"
  type        = list
  default     = ["benchmarking", "solr"]
}

variable "machine_labels" {
  description = "A map of key/value label pairs to assign to the instance"
  type        = map
  default = {
    product    = "solr"
    purpose    = "benchmarking"
    automation = "true"
  }
}

variable "network_name" {
  description = "The name of the network in which the VMs would be created"
  default     = "default"
}

variable "public_key_path" {
  description = "Path to public SSH key"
  default     = "id_rsa.pub"
}

variable "user" {
  description = "The user with which you can ssh into the VM. This user would be created if it does not exists"
  default     = "solruser"
}

variable "min_cpu_platform" {
  description = "The minimum CPU platform. For N2D instances, this is either 'AMD Rome' or 'AMD Milan', for other instances, refer to documentation"
  default = null
}

