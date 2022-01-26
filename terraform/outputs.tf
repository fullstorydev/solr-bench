output "zookeeper_details" {
  description = "Details of the Zookeeper machine"
  value       = module.zookeeper.instance_details
  sensitive = true
}

output "solr_node_details" {
  description = "Details of the solr machines"
  value       = module.solr_node.instance_details
  sensitive = true
}
