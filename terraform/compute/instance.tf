resource "google_compute_instance" "instance" {
  count = var.instance_count

  depends_on = [var.module_depends_on]

  name         = "${var.instance_name}-${count.index + 1}"
  machine_type = var.machine_type
  description  = var.instance_description

  desired_status = var.desired_status

  tags = var.tags

  labels = var.machine_labels

  metadata = {
    ssh-keys = "${var.user}:${file(var.public_key_path)}"
  }

  boot_disk {
    auto_delete = true
    device_name = var.device_name

    initialize_params {
      image = var.boot_image
      type  = var.disk_type
    }
  }

  scratch_disk {
    interface = "NVME"
  }

  # metadata_startup_script   = var.startup_script
  allow_stopping_for_update = var.allow_stopping_for_update

  network_interface {
    network = var.network_name
    access_config {
      # network_tier = "STANDARD"
    }
  }
}
