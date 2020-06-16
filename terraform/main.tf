module "zookeeper" {
  source = "./compute"

  instance_count       = 1
  instance_name        = var.zookeeper_instance_name
  instance_description = "Zookeeper node - benchmarking"
  machine_type         = var.zookeeper_machine_type

  device_name = var.zookeeper_device_name
  boot_image  = var.zookeeper_boot_image
  disk_type   = var.zookeeper_disk_type

  network_name = var.network_name

  user             = var.user
  public_key_path  = var.public_key_path

  tags = var.tags

  machine_labels = var.machine_labels

}

module "solr_node" {
  source = "./compute"

  module_depends_on = [module.zookeeper]

  instance_count       = var.solr_node_count
  instance_name        = var.solr_instance_name
  instance_description = "Solr node - benchmarking"
  machine_type         = var.solr_machine_type

  device_name = var.solr_device_name
  boot_image  = var.solr_boot_image
  disk_type   = var.solr_disk_type

  network_name = var.network_name

  user             = var.user
  public_key_path  = var.public_key_path

  tags = var.tags

  machine_labels = var.machine_labels

}
