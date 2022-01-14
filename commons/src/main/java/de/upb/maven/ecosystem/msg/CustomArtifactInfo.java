package de.upb.maven.ecosystem.msg;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomArtifactInfo {

  private String classifier;
  private String groupId;
  private String artifactVersion;
  private String fileExtension;
  private String packaging;
  private String artifactId;
  private String distribution;
  private String licenseUrl;
  private String bundleLicense;
  private String repoURL;
}
