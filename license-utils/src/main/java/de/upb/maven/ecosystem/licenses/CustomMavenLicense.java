package de.upb.maven.ecosystem.licenses;

import org.apache.maven.model.License;

public class CustomMavenLicense extends License {

  public String getLicenseText() {
    return licenseText;
  }

  public void setLicenseText(String licenseText) {
    this.licenseText = licenseText;
  }

  String licenseText;
}
