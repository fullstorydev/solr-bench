variable "instance_count" {
  description = "Number of instances to create"
  default     = 1
}

variable "desired_status" {
  description = "The desired state of the VM - RUNNING/TERMINATED"
  default     = "RUNNING"
}
variable "instance_name" {
  description = "The virtual machine name"
  default     = "vm-instance"
}

variable "instance_description" {
  description = "A description for the virtual machine"
  default     = "About this VM"
}

variable "machine_type" {
  description = "The machine type to create"
  default     = "f1-micro"
}

variable "boot_image" {
  description = "The image from which to initialize the boot disk."
  default     = "debian-cloud/debian-9"
}

variable "disk_type" {
  description = "The GCE disk type"
  default     = "pd-standard"
}

variable "device_name" {
  description = "Name with which attached disk will be accessible. On the instance, this device will be /dev/disk/by-id/google-{{device_name}}"
  default     = "default-device"
}

variable "allow_stopping_for_update" {
  description = "Allow VM to stop for update?"
  default     = true
}

variable "network_name" {
  description = "The name or self_link of the network to attach this interface to"
  default     = "default"
}

variable "tags" {
  description = "A list of tags to attach to the instance"
  type        = list
  default     = []
}

variable "machine_labels" {
  description = "A map of key/value label pairs to assign to the instance"
  type        = map
  default     = {}
}

variable "module_depends_on" {
  type    = any
  default = null
}

variable "public_key_path" {
  description = "Path to the public key"
}

variable "user" {
  description = "The user with which you can ssh into the VM"
}
