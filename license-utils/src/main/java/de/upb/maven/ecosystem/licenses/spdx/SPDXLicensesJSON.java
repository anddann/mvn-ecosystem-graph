package de.upb.maven.ecosystem.licenses.spdx;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SPDXLicensesJSON {

  private String licenseListVersion;
  private SpdxLicenseJson[] licenses;
  private String releaseDate;

  public String getLicenseListVersion() {
    return licenseListVersion;
  }

  public void setLicenseListVersion(String licenseListVersion) {
    this.licenseListVersion = licenseListVersion;
  }

  public SpdxLicenseJson[] getLicenses() {
    return licenses;
  }

  public void setLicenses(SpdxLicenseJson[] licenses) {
    this.licenses = licenses;
  }

  public String getReleaseDate() {
    return releaseDate;
  }

  public void setReleaseDate(String releaseDate) {
    this.releaseDate = releaseDate;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class SpdxLicenseJson {
    private String reference;
    private boolean isDeprecatedLicenseId;
    private String detailsUrl;
    private int referenceNumber;
    private String name;
    private String licenseId;
    private String[] seeAlso;
    private boolean isOsiApproved;

    public String getReference() {
      return reference;
    }

    public void setReference(String reference) {
      this.reference = reference;
    }

    public boolean isDeprecatedLicenseId() {
      return isDeprecatedLicenseId;
    }

    public void setDeprecatedLicenseId(boolean deprecatedLicenseId) {
      isDeprecatedLicenseId = deprecatedLicenseId;
    }

    public String getDetailsUrl() {
      return detailsUrl;
    }

    public void setDetailsUrl(String detailsUrl) {
      this.detailsUrl = detailsUrl;
    }

    public int getReferenceNumber() {
      return referenceNumber;
    }

    public void setReferenceNumber(int referenceNumber) {
      this.referenceNumber = referenceNumber;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getLicenseId() {
      return licenseId;
    }

    public void setLicenseId(String licenseId) {
      this.licenseId = licenseId;
    }

    public String[] getSeeAlso() {
      return seeAlso;
    }

    public void setSeeAlso(String[] seeAlso) {
      this.seeAlso = seeAlso;
    }

    public boolean isOsiApproved() {
      return isOsiApproved;
    }

    public void setOsiApproved(boolean osiApproved) {
      isOsiApproved = osiApproved;
    }
  }
}
